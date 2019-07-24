package com.learn.spring.mvcframework.demo.action;

import com.learn.spring.mvcframework.annotation.LAutowired;
import com.learn.spring.mvcframework.annotation.LController;
import com.learn.spring.mvcframework.annotation.LRequestMapping;
import com.learn.spring.mvcframework.annotation.LRequestParam;
import com.learn.spring.mvcframework.demo.service.IDemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@LController
@LRequestMapping("/demo")
public class DemoAction {

  	@LAutowired
	private IDemoService demoService;

	@LRequestMapping("/query")
	public void query(HttpServletRequest req, HttpServletResponse resp,
					  @LRequestParam("name") String name){
		String result = demoService.gets(name);
//		String result = "My name is " + name;
		try {
			resp.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@LRequestMapping("/add")
	public void add(HttpServletRequest req, HttpServletResponse resp,
					@LRequestParam("a") Integer a, @LRequestParam("b") Integer b){
		try {
			resp.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@LRequestMapping("/remove")
	public void remove(HttpServletRequest req,HttpServletResponse resp,
					   @LRequestParam("id") Integer id){
	}

}
