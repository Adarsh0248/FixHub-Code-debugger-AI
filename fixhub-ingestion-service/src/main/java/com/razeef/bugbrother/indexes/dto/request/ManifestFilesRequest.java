package com.razeef.bugbrother.indexes.dto.request;

import java.util.List;

public record ManifestFilesRequest(
        List<ManifestFileInput> files
) {
}


