package com.back.support.query;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProbeMapper {

    @Select("SELECT name FROM test_sample WHERE id = #{id}")
    String findName(long id);

    @Update("UPDATE test_sample SET name = #{name} WHERE id = #{id}")
    int rename(long id, String name);
}
