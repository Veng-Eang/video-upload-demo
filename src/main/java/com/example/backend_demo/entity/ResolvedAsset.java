package com.example.backend_demo.entity;

import java.nio.file.Path;

public record ResolvedAsset(Path path, boolean exists) {
}
