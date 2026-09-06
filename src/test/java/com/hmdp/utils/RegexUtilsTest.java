package com.hmdp.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class RegexUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"a1B2c3", "123456"})
    public void testIsCodeInvalid_valid(String code){
        assertFalse(RegexUtils.isCodeInvalid(code));
    }

    @Test
    public void testIsCodeInvalid_invalid(){
        assertTrue(RegexUtils.isCodeInvalid(""));
    }

    @Test
    public void testIsEmailInvalid_valid(){
        assertFalse(RegexUtils.isEmailInvalid("user@example.com"));
    }

    @Test
    public void testIsEmailInvalid_invalid(){
        assertTrue(RegexUtils.isEmailInvalid("a1B2c3"));
    }

    @Test
    public void testIsPhoneInvalid_valid(){
        assertFalse(RegexUtils.isPhoneInvalid("13800138000"));
    }

    @Test
    public void testIsPhoneInvalid_invalid(){
        assertTrue(RegexUtils.isPhoneInvalid("a1B2c3"));
    }
}
