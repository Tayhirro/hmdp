package com.hmdp.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class RegexUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"a1B2c3","", "123456"})
    public void testIsCodeInvalid_blank(String phone){
        assertFalse(RegexUtils.isCodeInvalid(phone));
    }
    @Test
    public void testIsEmailInvalid_blank(){
        assertFalse(RegexUtils.isEmailInvalid("a1B2c3"));
    }
    @Test
    public void testIsPhoneInvalid_blank(){
        assertFalse(RegexUtils.isPhoneInvalid("a1B2c3"));
    }


}
