package com.selenium.sdjubbs.common.bean;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class Article implements Serializable {
    private Integer id;
    private String title;
    private String content;
    private Integer blockId;
    private String blockName;
    private Integer authorId;
    private String authorName;
    private String createTime;
    private Integer priority;
    private Integer seeNum;

    public Article() {
    }
}
