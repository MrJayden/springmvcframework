package com.learn.spring.mvcframework.servlet;

import com.learn.spring.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public class LDispatcherServlet extends HttpServlet {

    /**
     * 配置文件在web.xml中初始化参数中的param-name值
     */
    private static final String LOCATION = "contextConfigLocation";
    /**
     * 保存配置文件数据信息
     */
    private Properties properties = new Properties();
    /**
     * 保存包扫描到的所有类的类名
     */
    private List<String> classNames = new ArrayList();
    /**
     * 保存需要控制反转的类
     */
    private Map<String,Object> ioc = new HashMap<String,Object>();

    /**
     * 保存controller类，方法，参数，url的映射关系
     */
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1、 加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));
        // 2、扫描所有相关类
        doScanner(properties.getProperty("scanPackage"));
        // 3、初始化所有相关类的实例
        doInstance();
        // 4、依赖注入
        doAutowired();
        // 5、构造HandlerMapping
        initHandleMapping();
        // 6、等待请求，匹配URL，定位方法，反射调用执行
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("----------------------走了doGet");
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            this.doDispatcher(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception,Details:\r\n" + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "\r\n"));
        }
    }

    /**
     * 匹配URL
     * @param req
     * @param resp
     */
    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        System.out.println("--------匹配请求的URL【开始】");
       ioc.keySet().forEach(key->{
           System.out.println(key +"---->"+ioc.get(key));
       });
        try{
            Handler handler = getHander(req);
            if(null == handler){
                resp.getWriter().write("404 Not Found");
                return;
            }
            //获取方法参数列表
            Class<?>[] paramTypes = handler.method.getParameterTypes();

            //保存所有需要自动复制的参数值
            Object [] paramValues = new Object[paramTypes.length];
            Map<String,String[]> params = req.getParameterMap();
            for(Map.Entry<String,String[]> param:params.entrySet()){
                String value = Arrays.toString(param.getValue())
                        .replaceAll("\\[\\]","")
                        .replaceAll(",\\s",",");
                if(!handler.paramIndexMapping.containsKey(param.getKey())){
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }
            //设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
            handler.method.invoke(handler.controller,paramValues);
            System.out.println("--------匹配请求的URL【结束】");
        }catch (Exception e){
            e.printStackTrace();
             throw e;
        }
    }


    private Handler getHander(HttpServletRequest req) throws Exception {
        System.out.println("handleMapping: -------》"+handlerMapping);
        System.out.println("request url =========>"+req.getContextPath());
        if(handlerMapping.isEmpty()){ return null;}
        String url = req.getRequestURI();
        /**
        * 如果项目名称为test,你在浏览器中输入请求路径：http://localhost:8080/test/pc/list.jsp,
        * 那么req.getContextPath() 获取到的值为/test
        * req.getRequestURI() 获取到的值为/test/pc/list.jsp
        */
        String contextPath = req.getContextPath();
        //相当于将项目名去掉，滞留下controller的url路径和方法url路径
        url = url.replace(contextPath,"").replaceAll("/+","/");
        for(Handler handler:handlerMapping){
            try{
                if(handler.pattern.matcher(url).matches()){
                   return handler;
                }
                continue;
            }catch (Exception e){
                throw e;
            }
        }
        return null;
    }


    /**
     * 初始化HandleMapping
     * 将controller类的方法与url对应起来
     */
    private void initHandleMapping() {
        System.out.println("--------初始化HandlerMapping【开始】");
        if(ioc.isEmpty()){return;}
        //通过遍历，将标有LController的类中的方法找出来
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(LController.class)){continue;}
            String url = "";
            //获取Controller的url配置
            if(clazz.isAnnotationPresent(LRequestMapping.class)){
                 url = clazz.getAnnotation(LRequestMapping.class).value();
            }
            //获取Method的url配置
            Method[] methods = clazz.getMethods();
            for(Method method:methods){
                //如果没有加LRequestMapping的方法直接忽略
                if(!method.isAnnotationPresent(LRequestMapping.class)){ continue; }
                //映射URL
                String regex = ("/"+url+method.getAnnotation(LRequestMapping.class).value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
                System.out.println("mapping  "+regex+", "+method);
            }
        }
        System.out.println("--------初始化HandlerMapping【结束】 ,handlerMappig size = "+handlerMapping.size());
        handlerMapping.forEach(obj-> System.out.println(obj.toString()));
    }

    /**
     * 依赖注入
     */
    private void doAutowired() {
        System.out.println("-------- 依赖注入【开始】");
        if(ioc.isEmpty()){return;}
        for(Map.Entry<String,Object> entry:ioc.entrySet()){
             Field[] fields = entry.getValue().getClass().getDeclaredFields();
             for(Field field : fields){
                 if(!field.isAnnotationPresent(LAutowired.class)){
                     continue;
                 }
                 LAutowired autowired = field.getAnnotation(LAutowired.class);
                 String beanName = autowired.value().trim();
                 if("".equals(beanName)){
                     beanName = field.getType().getName();
                 }
                 field.setAccessible(true);
                 try {
                     field.set(entry.getValue(),ioc.get(beanName));
                     System.out.println("--------class = "+entry.getValue().getClass().getSimpleName()+" ,field = "+entry.getValue()+" ,field value = "+ioc.get(beanName));
                 } catch (IllegalAccessException e) {
                     e.printStackTrace();
                     continue;
                 }
             }
        }
        System.out.println("-------- 依赖注入【结束】");
    }

    /**
     * 初始化所有相关类的实例，并保存到IOC容器中
     */
    private void doInstance() {
        System.out.println("--------初始化所有相关类实例【开始】");
        if(classNames.size()<=0){ return;}
        try {
            for(String className: classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(LController.class)) {
                    ioc.put(lowerFirst(clazz.getSimpleName()), clazz.newInstance());
                } else if (clazz.isAnnotationPresent(LService.class)) {
                    String beanName = clazz.getAnnotation(LService.class).value();
                    //如果自己设置了名字，就用自己设置的
                    if (!"".equals(beanName.trim())) {
                        ioc.put(beanName, clazz.newInstance());
                        continue;
                    }
                    //如果自己没设名字，就按照接口类型创造一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i : interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else {
                    continue;
                }
            }
            System.out.println("--------初始化所有相关类实例【结束】，ioc size = "+ioc.size());
            ioc.forEach((k,v)-> System.out.println(k+" = "+v));
        } catch (Exception e) {
                e.printStackTrace();
        }
    }


    /**
     * 加载配置文件信息
     * @param initParameter
     */
    private void doLoadConfig(String initParameter) {
        System.out.println("-------加载配置文件内容【开始】");
        InputStream is = null;
        try {
            is =  this.getClass().getClassLoader().getResourceAsStream(initParameter);
            properties.load(is);
            System.out.println("-------加载配置文件内容【结束】 ,properties size = "+properties.size());
            if(properties.size()>0){
            properties.forEach((key,value)->{System.out.println(key+" = "+value);});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
             if(null !=is){
                 try {
                     is.close();
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
             }
        }
    }


    /**
     *递归扫描出所有Class文件
     */
    private void doScanner(String packageName) {
        System.out.println("--------扫描所有Class文件【开始】");
        System.out.println("--------packageName = "+packageName);
        //将所有的包路径转换为文件路径
        URL url = this.getClass().getClassLoader().getResource(packageName.replace(".","/"));
        System.out.println("url    :  "+url);
        File dir = new File(url.getFile());
        for(File file : dir.listFiles()){
            //如果是文件夹，继续递归调用
            if(file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else{
                //将扫描到的所有类的类名加入到集合当中
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }
        System.out.println("--------扫描所有Class文件【结束】， className size = "+classNames.size());
        classNames.forEach(className->{ System.out.println(className); });
    }


    /**
     * 首字母小母
     * @param str
     * @return
     */
    private String lowerFirst(String str){
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }

    private class Handler{
        protected  Object controller;  //保存方法对应的实例
        protected  Method method;      //保存映射的方法
        protected  Pattern pattern;
        protected  Map<String,Integer> paramIndexMapping;  //参数顺序


        protected Handler(Pattern pattern,Object controller,Method method){
            this.controller = controller;
            this.pattern = pattern;
            this.method = method;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }


        private void putParamIndexMapping(Method method){
            //提取方法中加了注解的参数
            Annotation [] [] params = method.getParameterAnnotations();
            for(int i=0;i<params.length;i++){
                for(Annotation a: params[i]){
                    if(a instanceof LRequestParam){
                        String paramName =((LRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                             paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }
            //提取方法中的request和response参数
            Class<?>[]  paramTypes = method.getParameterTypes();
            for(int i=0;i<paramTypes.length;i++){
                Class<?> type = paramTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }
}
