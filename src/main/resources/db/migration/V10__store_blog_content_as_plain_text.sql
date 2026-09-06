-- 旧写链路曾把换行和 HTML 实体直接写入数据库；一次性还原为纯文本。
UPDATE `tb_blog`
SET `content` = REPLACE(
        REPLACE(
          REPLACE(
            REPLACE(
              REPLACE(
                REPLACE(`content`, '<br/>', CHAR(10)),
                '&lt;', '<'),
              '&gt;', '>'),
            '&quot;', '"'),
          '&#39;', ''''),
        '&amp;', '&');
