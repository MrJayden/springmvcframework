package com.learn.spring.mvcframework.demo.service.impl;

import com.learn.spring.mvcframework.annotation.LService;
import com.learn.spring.mvcframework.demo.service.IDemoService;

/**
 * 核心业务逻辑
 */
@LService
public class DemoService implements IDemoService {

	@Override
	public String gets(String name) {
		return "My name is " + name;
	}

}
