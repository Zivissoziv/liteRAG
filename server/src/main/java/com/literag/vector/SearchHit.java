package com.literag.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHit {

    private String vectorId;
    private Long docId;
    private Long kbId;
    private Integer seq;
    private String title;
    private String content;
    /** 相似度得分，0~1，越大越相关 */
    private double score;
    private String backend;
}
