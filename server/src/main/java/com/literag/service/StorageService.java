package com.literag.service;

import com.literag.common.BizException;
import com.literag.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final AppProperties props;

    public Path root() {
        Path root = Paths.get(props.getStorageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException("无法创建存储目录: " + root);
        }
        return root;
    }

    public String save(Long kbId, String ext, InputStream in) {
        String relative = "kb" + kbId + "/" + UUID.randomUUID().toString().replace("-", "")
                + (ext == null || ext.isBlank() ? "" : "." + ext.toLowerCase());
        Path target = root().resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException("文件保存失败: " + e.getMessage());
        }
        return relative;
    }

    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new BizException("文件路径为空");
        }
        Path path = root().resolve(relative).normalize();
        if (!path.startsWith(root())) {
            throw new BizException("非法的文件路径");
        }
        return path;
    }

    public void delete(String relative) {
        try {
            if (relative != null && !relative.isBlank()) {
                Files.deleteIfExists(resolve(relative));
            }
        } catch (Exception e) {
            log.warn("删除文件失败 {}: {}", relative, e.getMessage());
        }
    }

    public void write(Path path, byte[] data) {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                out.write(data);
            }
        } catch (IOException e) {
            throw new BizException("写入文件失败: " + e.getMessage());
        }
    }
}
