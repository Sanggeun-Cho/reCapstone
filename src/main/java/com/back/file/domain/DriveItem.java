package com.back.file.domain;

import com.back.common.entity.BaseEntity;
import com.back.file.support.ContentTypeResolver;
import com.back.file.support.ItemNames;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 스페이스의 파일 또는 폴더 (정책 제8장). 파일과 폴더가 한 이름 공간을 쓰므로 한 테이블에 둔다.
 * <p>
 * 휴지통으로 옮기면 그 항목에만 {@code trashedAt}을 표시하고 트리에서 떼어 낸다({@code parentId = null}).
 * 원래 폴더는 {@code originalParentId}에 남긴다. 하위 항목은 그대로 그 항목 아래에 남아 함께 휴지통에 있는 것이 된다.
 * 떼어 두므로 원래 폴더를 이동·영구 삭제해도 따로 지운 항목은 영향을 받지 않는다.
 */
@Getter
@Entity
@Table(name = "drive_item")
@SQLRestriction(BaseEntity.NOT_DELETED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriveItem extends BaseEntity {

    /** 폴더는 최상위를 포함해 10단계까지 (정책 8.3 제4항) */
    public static final int MAX_FOLDER_DEPTH = 10;

    public enum Type {
        FILE, FOLDER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long spaceId;

    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nameKey;

    private int depth;

    private Long size;

    private String contentType;

    private String detectedType;

    private boolean inlineViewable;

    private String storageKey;

    private Long uploaderId;

    private Instant trashedAt;

    private Long trashedBy;

    private Long originalParentId;

    public static DriveItem folder(long spaceId, Long parentId, int depth, String name) {
        DriveItem item = new DriveItem();
        item.spaceId = spaceId;
        item.parentId = parentId;
        item.type = Type.FOLDER;
        item.depth = depth;
        item.setName(name);
        return item;
    }

    public static DriveItem file(long spaceId, Long parentId, int depth, String name, Content content) {
        DriveItem item = new DriveItem();
        item.spaceId = spaceId;
        item.parentId = parentId;
        item.type = Type.FILE;
        item.depth = depth;
        item.setName(name);
        item.setContent(content);
        return item;
    }

    /** 덮어쓰기로 밀려난 이전 내용. 같은 이름의 새 휴지통 항목이 된다 (정책 8.4 제1항). */
    public DriveItem previousVersion(long actorId, Instant at) {
        DriveItem previous = new DriveItem();
        previous.spaceId = spaceId;
        previous.type = Type.FILE;
        previous.depth = depth;
        previous.name = name;
        previous.nameKey = nameKey;
        previous.size = size;
        previous.contentType = contentType;
        previous.detectedType = detectedType;
        previous.inlineViewable = inlineViewable;
        previous.storageKey = storageKey;
        previous.uploaderId = uploaderId;
        previous.originalParentId = parentId;
        previous.trashedAt = at;
        previous.trashedBy = actorId;
        return previous;
    }

    /** 내용을 바꾼다. id와 이름은 그대로 둔다 (덮어쓰기, 정책 8.4 제2항). */
    public void replaceContent(Content content) {
        setContent(content);
    }

    /** 이름을 바꾼다. 파일이면 저장소를 다시 읽지 않고 저장된 실제 형식으로 다시 판단한다 (정책 8.6 제2항). */
    public void rename(String name) {
        setName(name);
    }

    public void moveTo(Long parentId, int depth) {
        this.parentId = parentId;
        this.depth = depth;
    }

    public void trash(long actorId, Instant at) {
        this.originalParentId = parentId;
        this.parentId = null;
        this.trashedAt = at;
        this.trashedBy = actorId;
    }

    /** 휴지통에서 꺼낸다. 이름은 겹치면 번호가 붙은 이름이다. */
    public void restore(Long parentId, int depth, String name) {
        this.parentId = parentId;
        this.depth = depth;
        this.originalParentId = null;
        this.trashedAt = null;
        this.trashedBy = null;
        setName(name);
    }

    public boolean isFolder() {
        return type == Type.FOLDER;
    }

    public boolean isFile() {
        return type == Type.FILE;
    }

    public boolean isTrashed() {
        return trashedAt != null;
    }

    private void setName(String name) {
        this.name = ItemNames.normalize(name);
        this.nameKey = ItemNames.key(name);
        if (type == Type.FILE && detectedType != null) {
            contentType = ContentTypeResolver.byName(this.name);
            inlineViewable = ContentTypeResolver.inlineViewable(this.name, detectedType);
        }
    }

    private void setContent(Content content) {
        this.storageKey = content.storageKey();
        this.size = content.size();
        this.detectedType = content.detectedType();
        this.uploaderId = content.uploaderId();
        this.contentType = ContentTypeResolver.byName(name);
        this.inlineViewable = ContentTypeResolver.inlineViewable(name, detectedType);
    }

    /** 저장소에 올린 내용 */
    public record Content(String storageKey, long size, String detectedType, long uploaderId) {
    }
}
