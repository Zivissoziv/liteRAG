package com.literag.vector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecord {

    private String id;
    private Long docId;
    private Long kbId;
    private Integer seq;
    private String title;
    private String content;
    private float[] embedding;
}
