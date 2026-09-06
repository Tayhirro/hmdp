package com.hmdp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlogControllerLikeMappingTest {

    @Test
    void likeBlog_should_only_accept_put_on_explicitLikePath() throws NoSuchMethodException {
        Method method = BlogController.class.getMethod("likeBlog", Long.class);

        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertNotNull(mapping);
        assertEquals("/{id}/like", mapping.value()[0]);
        assertNull(method.getAnnotation(DeleteMapping.class));
    }

    @Test
    void unlikeBlog_should_only_accept_delete_on_explicitLikePath() throws NoSuchMethodException {
        Method method = BlogController.class.getMethod("unlikeBlog", Long.class);

        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertNotNull(mapping);
        assertEquals("/{id}/like", mapping.value()[0]);
        assertNull(method.getAnnotation(PutMapping.class));
    }
}
