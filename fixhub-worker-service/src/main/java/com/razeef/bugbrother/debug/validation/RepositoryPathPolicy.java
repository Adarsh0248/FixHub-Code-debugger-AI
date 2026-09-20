package com.razeef.bugbrother.debug.validation;

import com.razeef.bugbrother.debug.exception.ModelResponseValidationException;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RepositoryPathPolicy {

    public String normalize(String value) {
        String path = value == null
                ? ""
                : value.trim().replace('\\', '/');

        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        if (path.isBlank()
                || path.length() > 2000
                || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*")
                || Arrays.asList(path.split("/", -1)).contains("..")) {
            throw new ModelResponseValidationException(
                    "The model returned an unsafe repository path"
            );
        }

        return path;
    }
}
