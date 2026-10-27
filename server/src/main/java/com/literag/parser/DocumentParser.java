package com.literag.parser;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String ext);

    String parse(Path file) throws Exception;
}
