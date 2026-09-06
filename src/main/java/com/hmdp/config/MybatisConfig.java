package com.hmdp.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisConfig {
    /**
     * 注册 MyBatis-Plus 分页插件（MySQL 方言）。
     *
     * 使用场景：Spring 启动时创建 bean 并挂到 MyBatis-Plus 拦截器链；
     * 之后所有带 Page 参数的分页查询（如 {@link com.hmdp.service.impl.ShopServiceImpl}（店铺服务实现）、
     * {@link com.hmdp.service.blog.BlogQueryService}（博客查询服务）等）经 PaginationInnerInterceptor
     * 自动改写 SQL 加 LIMIT 并追加 count 查询，未注册时分页不生效。
     *
     * @return 已装配 MySQL 分页内部拦截器的 {@link MybatisPlusInterceptor}
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
