package com.back.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.file.domain.DriveItem;
import com.back.space.api.SpaceDto;
import com.back.support.FileTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 업로드의 기록 단계와 동시 요청 (DESIGN_REVIEW.md 6.8 구현 메모). 저장소 대역의 {@code afterPut}으로
 * "저장소 전송은 끝났지만 아직 기록하지 않은" 지점에서 멈춘다.
 */
class FileConcurrencyTest extends FileTestSupport {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private Future<MockHttpServletResponse> uploadAsync(String name, String content, String... params) {
        return executor.submit(() -> upload(BOB, s, List.of(file(name, content)), params).andReturn().getResponse());
    }

    @Test
    @DisplayName("FILE-30: 같은 폴더에 a.pdf 두 건이 기록 단계에서 겹치면 둘 다 201, a.pdf와 a (1).pdf")
    void concurrentSameName() throws Exception {
        session(BOB);
        CyclicBarrier bothUploaded = new CyclicBarrier(2);
        fileStorage.afterPut(() -> await(bothUploaded));

        Future<MockHttpServletResponse> first = uploadAsync("a.pdf", "1");
        Future<MockHttpServletResponse> second = uploadAsync("a.pdf", "2");

        assertThat(List.of(first.get(30, TimeUnit.SECONDS).getStatus(), second.get(30, TimeUnit.SECONDS).getStatus()))
                .containsOnly(201);
        assertThat(itemRepository.findAll()).extracting(DriveItem::getName)
                .containsExactlyInAnyOrder("a.pdf", "a (1).pdf");
    }

    @Test
    @DisplayName("FILE-30b: a.pdf에 REPLACE 두 건이 겹치면 둘 다 201, id 유지, 나중 기록이 현재 내용, 휴지통 2건")
    void concurrentReplace() throws Exception {
        long x = uploadOne(null, "a.pdf", "v1");
        CyclicBarrier bothUploaded = new CyclicBarrier(2);
        fileStorage.afterPut(() -> await(bothUploaded));

        Future<MockHttpServletResponse> first = uploadAsync("a.pdf", "v2", "onConflict=REPLACE");
        Future<MockHttpServletResponse> second = uploadAsync("a.pdf", "v3", "onConflict=REPLACE");
        MockHttpServletResponse r1 = first.get(30, TimeUnit.SECONDS);
        MockHttpServletResponse r2 = second.get(30, TimeUnit.SECONDS);

        assertThat(List.of(r1.getStatus(), r2.getStatus())).containsOnly(201);
        assertThat(List.<Number>of(JsonPath.read(r1.getContentAsString(), "$.created[0].id"),
                JsonPath.read(r2.getContentAsString(), "$.created[0].id")))
                .extracting(Number::longValue).containsOnly(x);
        assertThat(itemRepository.findBySpaceIdAndTrashedAtIsNotNull(s)).hasSize(2);
        String current = new String(fetch(downloadUrl(BOB, s, x, "ATTACHMENT")).body(), StandardCharsets.UTF_8);
        assertThat(current).isIn("v2", "v3");
        assertThat(itemRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("FILE-32: 저장소 전송 중에 인계가 시작되면 409 HANDOVER_IN_PROGRESS, DB 0행, 올린 객체 삭제")
    void handoverStartsDuringTransfer() throws Exception {
        session(BOB);
        CountDownLatch uploaded = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fileStorage.afterPut(() -> {
            uploaded.countDown();
            awaitLatch(release);
        });

        Future<MockHttpServletResponse> response = uploadAsync("a.txt", "x");
        assertThat(uploaded.await(30, TimeUnit.SECONDS)).isTrue();
        handoverStatus.start(s, g, bob, carol);
        release.countDown();

        MockHttpServletResponse result = response.get(30, TimeUnit.SECONDS);
        assertThat(result.getStatus()).isEqualTo(409);
        assertThat(result.getContentAsString()).contains("HANDOVER_IN_PROGRESS");
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    @Test
    @DisplayName("FILE-33: 저장소 전송 중에 담당자가 dave로 바뀌면 404, DB 0행, 올린 객체 삭제")
    void assigneeChangesDuringTransfer() throws Exception {
        session(BOB);
        CountDownLatch uploaded = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fileStorage.afterPut(() -> {
            uploaded.countDown();
            awaitLatch(release);
        });

        Future<MockHttpServletResponse> response = uploadAsync("a.txt", "x");
        assertThat(uploaded.await(30, TimeUnit.SECONDS)).isTrue();
        as(alice, () -> spaceService.forceReassign(alice, s, new SpaceDto.ForcedReassignmentReq(dave, "긴급")));
        release.countDown();

        assertThat(response.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(404);
        assertThat(itemRepository.count()).isZero();
        assertThat(objectCount(s)).isZero();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
