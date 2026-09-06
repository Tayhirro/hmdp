package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.service.blog.BlogCommandService;
import com.hmdp.service.blog.BlogLikeService;
import com.hmdp.service.blog.BlogQueryService;
import com.hmdp.service.feed.BlogFeedService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplFeedTest {

    @Mock
    private BlogFeedService blogFeedService;

    @Mock
    private BlogCommandService blogCommandService;

    @Mock
    private BlogLikeService blogLikeService;

    @Mock
    private BlogQueryService blogQueryService;

    @InjectMocks
    private BlogServiceImpl blogService;

    @Test
    void feedQuery_should_delegate_to_dedicated_pipeline_service() {
        Result expected = Result.ok();
        when(blogFeedService.query("cursor", "following", true)).thenReturn(expected);

        Result result = blogService.queryBlogFeed("cursor", "following", true);

        assertTrue(result.getSuccess());
        verify(blogFeedService).query("cursor", "following", true);
    }
}
