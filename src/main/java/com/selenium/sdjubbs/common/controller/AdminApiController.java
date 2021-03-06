package com.selenium.sdjubbs.common.controller;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.selenium.sdjubbs.common.api.Api;
import com.selenium.sdjubbs.common.bean.*;
import com.selenium.sdjubbs.common.config.SdjubbsSetting;
import com.selenium.sdjubbs.common.myenum.Counter;
import com.selenium.sdjubbs.common.service.*;
import com.selenium.sdjubbs.common.util.*;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.spring.web.json.Json;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin")
@Slf4j
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
@io.swagger.annotations.Api(value = "SDJUBBS Admin API", tags = "SDJUBBS Admin API")
public class AdminApiController {
    @Autowired
    private UserService userService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private SdjubbsSetting setting;
    @Autowired
    private RedisService redisService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private MessageService messageService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private ReplyService replyService;

    @GetMapping(Api.USER)
    @ApiOperation(value = "获取所有的用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(name = "limit", value = "每页记录数", required = true, example = "10"),
            @ApiImplicitParam(name = "order", value = "排序", required = false, example = "id asc"),
    })
    protected Result getAllUser(String name, String sessionId, String page, String limit, String order, String search) {
        int pageSize = 0;
        int pageNum = 0;
        List<User> users = null;
        PageInfo<User> pageInfo = null;
        try {
            pageSize = Integer.valueOf(limit);
            pageNum = Integer.valueOf(page);
            order = StringUtil.humpToLine(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        //获取第pageNum页,pageSize条内容
        PageHelper.startPage(pageNum, pageSize, order);
        if (search == null || "".equalsIgnoreCase(search)) {
            users = userService.getAllUser();
            pageInfo = new PageInfo<User>(users);
        } else {
            users = userService.getAllUserBySearch(search);
            pageInfo = new PageInfo<User>(users);
        }
        if (users == null) {
            return Result.failure("暂时没有用户");
        }
        return Result.success().add("pageInfo", pageInfo);
    }

    @PutMapping(Api.USER + "/{id}")
    @ApiOperation(value = "修改用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "id", value = "用户id", required = true, example = "1"),
            @ApiImplicitParam(name = "username", value = "用户名(长度:4-12)", required = false, example = "test"),
            @ApiImplicitParam(name = "password", value = "密码(md5加密)", required = false, example = "3a42503923d841ac9b7ec83eed03b450"),
            @ApiImplicitParam(name = "salt", value = "加密salt", required = false, example = "48aca90-2"),
            @ApiImplicitParam(name = "age", value = "年龄", required = false, example = "0"),
            @ApiImplicitParam(name = "gender", value = "性别(0:男,1:女,2:未知)", required = false, example = "2"),
            @ApiImplicitParam(name = "email", value = "邮箱", required = false, example = "895484122@qq.com"),
            @ApiImplicitParam(name = "phone", value = "手机号", required = false, example = "00000000000"),
            @ApiImplicitParam(name = "headPicture", value = "头像", required = false, example = "/common/images/avatar/default.jpg"),
            @ApiImplicitParam(name = "registerTime", value = "注册时间", required = false, example = "2019-09-01 23:37:49"),
            @ApiImplicitParam(name = "lastLoginTime", value = "上次登录时间", required = false, example = "2019-09-01 23:37:49"),
            @ApiImplicitParam(name = "status", value = "用户状态(0:有效,1:禁用)", required = false, example = "0"),
    })
    protected Result updateUser(String name, String sessionId, @PathVariable Integer id, User user, @RequestParam(value = "file", required = false) MultipartFile file) {
        //log.info("update user: " + user);
        if (file != null && file.getSize() != 0) {
            String savePath = PhotoUtil.saveFile(file, setting.getAvatarSavePath()).split("/static")[1];
            user.setHeadPicture(savePath);
        }
        if (user.getPassword() != null) {
            //通过MD5+随机salt加密写入数据库的密码
            String salt = UUID.randomUUID().toString().substring(1, 10);
            user.setSalt(salt);
            String password = MD5Util.dbEncryption(user.getPassword(), salt);
            user.setPassword(password);
        }
        Integer count = 0;
        try {
            count = userService.updateUser(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        }
        return Result.success();
    }

    @PostMapping(Api.USER)
    @ApiOperation(value = "新增用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "username", value = "用户名(长度:4-12)", required = false, example = "test"),
            @ApiImplicitParam(name = "password", value = "密码(md5加密)", required = false, example = "3a42503923d841ac9b7ec83eed03b450"),
            @ApiImplicitParam(name = "age", value = "年龄", required = false, example = "0"),
            @ApiImplicitParam(name = "gender", value = "性别(0:男,1:女,2:未知)", required = false, example = "2"),
            @ApiImplicitParam(name = "email", value = "邮箱", required = true, example = "895484122@qq.com"),
            @ApiImplicitParam(name = "phone", value = "手机号", required = false, example = "00000000000"),
            @ApiImplicitParam(name = "headPicture", value = "头像", required = false, example = "/common/images/avatar/default.jpg"),
    })
    protected Result addUser(String name, String sessionId, @Valid User user, BindingResult bindingResult, @RequestParam(value = "file", required = false) MultipartFile file) {
        //log.info("add user: " + user);
        if (bindingResult.hasErrors()) {
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for (ObjectError error : allErrors) {
                return Result.failure(Constant.REGISTER_USER_FORMAT_ERROR_CODE, error.getDefaultMessage());
            }
        }
        if (file != null && file.getSize() != 0) {
            String savePath = PhotoUtil.saveFile(file, setting.getAvatarSavePath()).split("/static")[1];
            user.setHeadPicture(savePath);
        } else {
            user.setHeadPicture(Constant.DEFAULT_HEAD_PICTURE);
        }
        //通过MD5+随机salt加密写入数据库的密码
        String salt = UUID.randomUUID().toString().substring(1, 10);
        user.setSalt(salt);
        String password = MD5Util.dbEncryption(user.getPassword(), salt);
        user.setPassword(password);
        user.setRegisterTime(TimeUtil.getTime());
        user.setLastLoginTime(TimeUtil.getTime());
        user.setStatus(0);
        user.setRole(0);
        userService.addUser(user);
        return Result.success();
    }


    @DeleteMapping(Api.USER + "/{id}")
    @ApiOperation(value = "删除用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "id", value = "用户id", required = true, example = "1"),
    })
    protected Result deleteUser(String name, String sessionId, @PathVariable Integer id) {
        Integer count = 0;
        try {
            count = userService.deleteUser(id);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.USERS)
    @ApiOperation(value = "批量删除用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "ids", value = "用户id集合，逗号进行分割", required = true, example = "1"),
    })
    protected Result deleteUserByBatch(String name, String sessionId, @RequestParam("ids") String ids) {
        List<Integer> idList = new ArrayList<>();
        String[] idsTemp = ids.split(",");
        for (String ip : idsTemp) {
            idList.add(Integer.valueOf(ip));
        }
        userService.deleteUserByBatch(idList);
        return Result.success();
    }


    @GetMapping(Api.VERIFY_CODE)
    @ApiOperation(value = "获取验证码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "width", value = "验证码图片宽", required = true, example = "180"),
            @ApiImplicitParam(name = "height", value = "验证码图片高", required = true, example = "50"),
    })
    public Result getVerifyCode(int width, int height, HttpServletRequest request) {
        String ip = MD5Util.md5(request.getRemoteAddr()).substring(0, 10);
        String imagePath = "/common/" + setting.getVerifyCodeSavePath().split("/common/")[1];
        String savePath = System.getProperty("user.dir") + setting.getVerifyCodeSavePath();
        FileUtil.deleteFilesWithPrefix(savePath, ip);
        String imageName = ip + "_" + System.currentTimeMillis();
        String verifyCode = "";
        String recordId = "";
        String verifyCodeKey = "";
        try {
            verifyCode = VerifyCodeUtil.drawVerifyCode(width, height, savePath, imageName);
            recordId = System.currentTimeMillis() + UUID.randomUUID().toString();
            verifyCodeKey = "verifycode:" + ip + ":" + recordId;
            //60秒后验证码失效
            redisService.set(verifyCodeKey, verifyCode, 60);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.success().add("img", imagePath + "/" + imageName).add("recordId", recordId);
    }


    //--------------------------------登录退出管理-----------------------------

    @PostMapping(Api.LOGIN)
    @ApiOperation(value = "登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "username", value = "用户名(长度:4-12)", required = true, example = "test"),
            @ApiImplicitParam(name = "password", value = "密码(md5加密)", required = true, example = "3a42503923d841ac9b7ec83eed03b450"),
            @ApiImplicitParam(name = "verifyCode", value = "验证码", required = true, example = "3111"),
            @ApiImplicitParam(name = "recordId", value = "验证码唯一标识", required = true, example = "3a42503923d841ac9b7ec83eed03b450")
    })
    public Result login(String username, String password, String verifyCode, String recordId, HttpServletRequest request, HttpSession session) {
        String ip = MD5Util.md5(request.getRemoteAddr()).substring(0, 10);
        String verifyCodeKey = "verifycode:" + ip + ":" + recordId;
        String realVerifyCode = redisService.get(verifyCodeKey);
        if (verifyCode == null || realVerifyCode == null || (!verifyCode.equalsIgnoreCase(realVerifyCode))) {
            return Result.failure(Constant.VERIFY_CODE_WRONG_CODE, Constant.VERIFY_CODE_WRONG);
        }
        //log.info("username: " + username + " password: " + password + " verifyCode: " + verifyCode + " recordId: " + recordId);
        Subject subject = SecurityUtils.getSubject();
        //log.info("用户是否登录,isAuthenticated: " + subject.isAuthenticated());
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        } else if (user.getRole() == 0) {//普通用户
            return Result.failure(Constant.LOGIN_USER_NOT_ADMIN_CODE, Constant.LOGIN_USER_NOT_ADMIN);
        }
        // 判断当前用户是否登录
//        if (!subject.isAuthenticated()) {
        String salt = user.getSalt();
        String realPassword = MD5Util.dbEncryption(password, salt);
        // 将用户名和密码封装
        UsernamePasswordToken token = new UsernamePasswordToken(username, realPassword);
        try {
            // 登录
            subject.login(token);
            //log.info("login success");
            //存入redis key:username value: md5(ip+sessionId)
            String sessionId = session.getId();
            redisService.set("admin:name:" + username, MD5Util.md5(request.getRemoteAddr() + sessionId));
            return Result.success().add("username", username).add("sessionId", sessionId);

        } catch (AuthenticationException e) {
            //log.info("login failure");
            e.printStackTrace();
            return Result.failure(Constant.LOGIN_USER_WRONG_PASSWORD_CODE, Constant.LOGIN_USER_WRONG_PASSWORD);
        }
//        }
    }

    @GetMapping(Api.LOGOUT)
    @ApiOperation(value = "退出登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result logout(String name, String sessionId) {
        redisService.delete("admin:name:" + name);
        return Result.success();
    }


    //--------------------------------文章管理-----------------------------
    @GetMapping(Api.ARTICLE)
    @ApiOperation(value = "获取所有的文章")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(name = "limit", value = "每页记录数", required = true, example = "10"),
            @ApiImplicitParam(name = "order", value = "排序", required = false, example = "id asc"),
    })
    protected Result getAllArticle(String name, String sessionId, String page, String limit, String order, String search) {
        int pageSize = 0;
        int pageNum = 0;
        List<Article> articles = null;
        PageInfo<Article> pageInfo = null;
        try {
            pageSize = Integer.valueOf(limit);
            pageNum = Integer.valueOf(page);
            order = StringUtil.humpToLine(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        //获取第pageNum页,pageSize条内容
        PageHelper.startPage(pageNum, pageSize, order);
        if (search == null || "".equalsIgnoreCase(search)) {
            articles = articleService.getAllArticle();
            pageInfo = new PageInfo<>(articles);
        } else {
            articles = articleService.getAllArticleBySearch(search);
            pageInfo = new PageInfo<>(articles);
        }
        if (articles == null) {
            return Result.failure("暂时没有文章");
        }
        return Result.success().add("pageInfo", pageInfo);
    }

    @PutMapping(Api.ARTICLE + "/{id}")
    @ApiOperation(value = "修改文章")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result updateArticle(String name, String sessionId, @PathVariable Integer id, Article article) {
        //log.info("update article: " + article);

        Integer count = 0;
        try {
            count = articleService.updateArticle(article);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.ARTICLE + "/{id}")
    @ApiOperation(value = "删除文章")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "id", value = "文章id", required = true, example = "1"),
    })
    protected Result deleteArticle(String name, String sessionId, @PathVariable Integer id) {
        Integer count = 0;
        try {
            count = articleService.deleteArticle(id);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.ARTICLES)
    @ApiOperation(value = "批量删除文章")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "ids", value = "文章id集合，逗号进行分割", required = true, example = "1"),
    })
    protected Result deleteArticleByBatch(String name, String sessionId, @RequestParam("ids") String ids) {
        List<Integer> idList = new ArrayList<>();
        String[] idsTemp = ids.split(",");
        for (String ip : idsTemp) {
            idList.add(Integer.valueOf(ip));
        }
        articleService.deleteArticleByBatch(idList);
        return Result.success();
    }

    //-------------------------------------板块相关------------------------------

    /**
     * method: get
     * url: /block
     * description: 获得所有板块
     */
    @GetMapping(Api.BLOCK_ALL)
    @ApiOperation(value = "获得所有板块")
    public Result getAllBlock() {
        List<Block> blocks = blockService.getAllBlock();
        return Result.success().add("blocks", blocks);
    }

    @GetMapping(Api.BLOCK)
    @ApiOperation(value = "获得所有板块(分页)")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(name = "limit", value = "每页记录数", required = true, example = "10"),
            @ApiImplicitParam(name = "order", value = "排序", required = false, example = "id asc"),
    })
    protected Result getAllBlock(String name, String sessionId, String page, String limit, String order, String search) {
        int pageSize = 0;
        int pageNum = 0;
        List<Block> blocks = null;
        PageInfo<Block> pageInfo = null;
        try {
            pageSize = Integer.valueOf(limit);
            pageNum = Integer.valueOf(page);
            order = StringUtil.humpToLine(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        //获取第pageNum页,pageSize条内容
        PageHelper.startPage(pageNum, pageSize, order);
        if (search == null || "".equalsIgnoreCase(search)) {
            blocks = blockService.getAllBlock();
            pageInfo = new PageInfo<>(blocks);
        } else {
            blocks = blockService.getAllBlockBySearch(search);
            pageInfo = new PageInfo<>(blocks);
        }
        if (blocks == null) {
            return Result.failure("暂时没有板块");
        }
        return Result.success().add("pageInfo", pageInfo);
    }

    @PostMapping(Api.BLOCK)
    @ApiOperation(value = "新增板块")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "title", value = "版块名", required = false, example = "test"),
            @ApiImplicitParam(name = "blockPicture", value = "板块封面", required = false, example = "/common/images/avatar/default.jpg"),
    })
    protected Result addBlock(String name, String sessionId, Block block, @RequestParam(value = "file", required = false) MultipartFile file) {
        if (file != null && file.getSize() != 0) {
            String savePath = PhotoUtil.saveFile(file, setting.getBlockPictureSavePath()).split("/static")[1];
            block.setBlockPicture(savePath);
        } else {
            block.setBlockPicture(Constant.DEFAULT_HEAD_PICTURE);
        }
        block.setArticleNum(0);
        block.setSaveNum(0);
        User user = userService.getUserByUsername(name);
        if (user == null) {
            return Result.failure(Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        } else {
            block.setAuthorId(user.getId());
            block.setAuthorName(name);
            block.setCreateTime(TimeUtil.getTime());
            blockService.addBlock(block);
        }
        return Result.success();
    }

    @PutMapping(Api.BLOCK + "/{id}")
    @ApiOperation(value = "修改板块")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "id", value = "板块id", required = true, example = "1"),
            @ApiImplicitParam(name = "title", value = "板块名", required = false, example = "test"),
            @ApiImplicitParam(name = "blockPicture", value = "板块封面", required = false)
    })
    protected Result updateBlock(String name, String sessionId, @PathVariable Integer id, Block block, @RequestParam(value = "file", required = false) MultipartFile file) {
        if (file != null && file.getSize() != 0) {
            String savePath = PhotoUtil.saveFile(file, setting.getAvatarSavePath()).split("/static")[1];
            block.setBlockPicture(savePath);
        }
        blockService.updateBlock(block);
        return Result.success();
    }


    //---------------------------------------新增加文章------------------------------
    @PostMapping(Api.ARTICLE)
    @ApiOperation(value = "新增文章")
    protected Result addArticle(String name, String sessionId, Article article) {
        //log.info("name: " + name + " article: " + article);
        Block block = blockService.getBlockById(article.getBlockId());
        article.setBlockName(block.getTitle());
        User user = userService.getUserByUsername(name);
        article.setAuthorId(user.getId());
        article.setAuthorName(user.getUsername());
        article.setCreateTime(TimeUtil.getTime());
        if (SensitiveWordUtil.checkSenstiveWord(System.getProperty("user.dir") + setting.getSensitiveWordPath(), article.getContent())) {
            article.setContent(SensitiveWordUtil.filterInfoAfter(System.getProperty("user.dir") + setting.getSensitiveWordPath(), article.getContent()));
        }
        articleService.addArticle(article);
        block.setArticleNum(block.getArticleNum() + 1);
        blockService.updateBlock(block);
        return Result.success();
    }


    @PostMapping(Api.UPLOAD_IMAGE)
    public JSONObject mdUploadImage(@RequestParam(value = "editormd-image-file", required = true) MultipartFile file) {
        //log.info("upload Image: " + file);
        String savePath = PhotoUtil.saveFile(file, setting.getArticleImageSavePath()).split("/static")[1];
        //log.info("uploadImageSavePath: " + savePath);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", Constant.WEB_ROOT + savePath);
        jsonObject.put("success", 1);
        jsonObject.put("message", "upload success!");
        return jsonObject;
    }


    //--------------------------------留言管理-----------------------------

    @GetMapping(Api.FEATURE_MESSAGE)
    @ApiOperation(value = "获取所有的留言")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(name = "limit", value = "每页记录数", required = true, example = "10"),
            @ApiImplicitParam(name = "order", value = "排序", required = false, example = "id asc"),
    })
    protected Result getAllMessage(String name, String sessionId, String page, String limit, String order) {
        int pageSize = 0;
        int pageNum = 0;
        try {
            pageSize = Integer.valueOf(limit);
            pageNum = Integer.valueOf(page);
            order = StringUtil.humpToLine(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        //获取第pageNum页,pageSize条内容
        PageHelper.startPage(pageNum, pageSize, order);
        List<Message> messages = messageService.getAllMessageForAdmin();
        PageInfo<Message> pageInfo = new PageInfo<>(messages);
        if (messages == null) {
            return Result.failure("暂时没有留言");
        }
        return Result.success().add("pageInfo", pageInfo);
    }

    @PutMapping(Api.FEATURE_MESSAGE + "/{id}")
    @ApiOperation(value = "修改留言状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result updateMessage(String name, String sessionId, @PathVariable Integer id, Message message) {
        //log.info("update article: " + article);

        Integer count = 0;
        try {
            count = messageService.updateMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.MESSAGE_NOT_EXIST_CODE, Constant.MESSAGE_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.FEATURE_MESSAGE + "/{id}")
    @ApiOperation(value = "删除留言")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result deleteMessage(String name, String sessionId, @PathVariable Integer id) {
        Integer count = 0;
        try {
            count = messageService.deleteMessage(id);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.MESSAGE_NOT_EXIST_CODE, Constant.MESSAGE_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.FEATURE_MESSAGES)
    @ApiOperation(value = "批量删除留言")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "ids", value = "留言id集合，逗号进行分割", required = true, example = "1"),
    })
    protected Result deleteMessageByBatch(String name, String sessionId, @RequestParam("ids") String ids) {
        List<Integer> idList = new ArrayList<>();
        String[] idsTemp = ids.split(",");
        for (String ip : idsTemp) {
            idList.add(Integer.valueOf(ip));
        }
        messageService.deleteMessageByBatch(idList);
        return Result.success();
    }

    @GetMapping(Api.FEATURE_MESSAGE_NEW_COUNT)
    @ApiOperation(value = "获取新增留言数量")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result getMessageNewCount(String name, String sessionId) {
        Integer newMessageCount = messageService.getNewMessageCount();
        return Result.success().add("newMessageCount", newMessageCount);
    }

    //--------------------------------评论管理-----------------------------

    @GetMapping(Api.COMMENT)
    @ApiOperation(value = "获取所有的评论")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "page", value = "页数", required = true, example = "1"),
            @ApiImplicitParam(name = "limit", value = "每页记录数", required = true, example = "10"),
            @ApiImplicitParam(name = "order", value = "排序", required = false, example = "id asc"),
    })
    protected Result getAllComment(String name, String sessionId, String page, String limit, String order, String search) {
        int pageSize = 0;
        int pageNum = 0;
        List<Comment> comments = null;
        PageInfo<Comment> pageInfo = null;

        try {
            pageSize = Integer.valueOf(limit);
            pageNum = Integer.valueOf(page);
            order = StringUtil.humpToLine(order);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        //获取第pageNum页,pageSize条内容
        PageHelper.startPage(pageNum, pageSize, order);
        if (search == null || "".equalsIgnoreCase(search)) {
            comments = commentService.getAllComment();
            pageInfo = new PageInfo<>(comments);
        } else {
            comments = commentService.getAllCommentBySearch(search);
            pageInfo = new PageInfo<>(comments);
        }
        if (comments == null) {
            return Result.failure("暂时没有评论");
        }
        return Result.success().add("pageInfo", pageInfo);
    }

    @PutMapping(Api.COMMENT + "/{id}")
    @ApiOperation(value = "修改评论状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result updateComment(String name, String sessionId, @PathVariable Integer id, Comment comment) {
        //log.info("update article: " + article);

        Integer count = 0;
        try {
            count = commentService.updateComment(comment);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.COMMENT_NOT_EXIST_CODE, Constant.COMMENT_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.COMMENT + "/{id}")
    @ApiOperation(value = "删除评论")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result deleteComment(String name, String sessionId, @PathVariable Integer id) {
        Integer count = 0;
        try {
            count = commentService.deleteComment(id);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure(Constant.REQUEST_PARAM_FORMAT_ERROR_CODE, Constant.REQUEST_PARAM_FORMAT_ERROR);
        }
        if (count == 0) {
            return Result.failure(Constant.COMMENT_NOT_EXIST_CODE, Constant.COMMENT_NOT_EXIST);
        }
        return Result.success();
    }

    @DeleteMapping(Api.COMMENTS)
    @ApiOperation(value = "批量删除评论")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
            @ApiImplicitParam(name = "ids", value = "评论id集合，逗号进行分割", required = true, example = "1"),
    })
    protected Result deleteCommentByBatch(String name, String sessionId, @RequestParam("ids") String ids) {
        List<Integer> idList = new ArrayList<>();
        String[] idsTemp = ids.split(",");
        for (String ip : idsTemp) {
            idList.add(Integer.valueOf(ip));
        }
        commentService.deleteCommentByBatch(idList);
        return Result.success();
    }

    @GetMapping(Api.COMMENT_REPORTED_COUNT)
    @ApiOperation(value = "获取被举报评论数量")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result getReportedCommentCount(String name, String sessionId) {
        Integer reportedCommentCount = commentService.getReportedCommentCount();
        return Result.success().add("reportedCommentCount", reportedCommentCount);
    }

    //--------------------------------二维码管理-----------------------------
    @DeleteMapping(Api.FEATURE_QR)
    @ApiOperation(value = "清空二维码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "登录身份凭证", required = true, example = "test"),
            @ApiImplicitParam(name = "sessionId", value = "cookie中存的值", required = true, example = "A7D3515256A097709011A5EBB86D9FEF"),
    })
    protected Result deleteAllQr(String name, String sessionId) {
        String savePath = System.getProperty("user.dir") + setting.getQrSavePath();
        FileUtil.deleteAllFilesUnderDir(savePath);
        return Result.success();
    }

    //---------------------------服务器信息管理----------------------------------
    @GetMapping(Api.SERVER_SYSTEM_INFO)
    @ApiOperation(value = "获取系统信息")
    protected Result getSystemInfo(String name, String sessionId) {
        SystemInfo systemInfo = ServerInfoUtil.getSystemInfo();
        return Result.success().add("systemInfo", systemInfo);
    }

    //----------------------------echarts图表显示-----------------

    @GetMapping(Api.STATISTICS_COUNT_INFO)
    @ApiOperation(value = "获取数量信息")
    protected Result getCountInfo(String name, String sessionId) {
        Map infos = new HashMap<String, Integer>();
        infos.put(Counter.USER.getName(), userService.getUserCount());
        infos.put(Counter.MESSAGE.getName(), messageService.getMessageCount());
        infos.put(Counter.BLOCK.getName(), blockService.getBlockCount());
        infos.put(Counter.ARTICLE.getName(), articleService.getArticleCount());
        infos.put(Counter.COMMENT.getName(), commentService.getCommentCount());
        infos.put(Counter.REPLEY.getName(), replyService.getReplyCount());
        return Result.success().add("infos", infos);
    }

    @GetMapping(Api.STATISTICS_ARTICLE_INFO)
    @ApiOperation(value = "获取文章排名信息")
    protected Result getArticleInfo(String name, String sessionId,int num) {
        List<TopArticleInfo> infos = articleService.getTopArticle(num);
        return Result.success().add("infos", infos);
    }

    @GetMapping(Api.STATISTICS_REGISTER_INFO)
    @ApiOperation(value = "获取注册用户信息")
    protected Result getRegisterUserInfo(String name, String sessionId, int num) {
        List<RegisterUserInfo> infos = userService.getUserOrderByRegisterTime(num);
        return Result.success().add("infos", infos);
    }

    @GetMapping(Api.STATISTICS_LOGIN_INFO)
    @ApiOperation(value = "获取登录信息")
    protected Result getLoginUserInfo(String name, String sessionId, int num) {
        List<LoginUserInfo> infos = userService.getUserOrderByLoginTime(num);
        return Result.success().add("infos", infos);
    }

    @GetMapping(Api.STATISTICS_MEMORY_INFO)
    @ApiOperation(value = "获取内存信息")
    protected Result getMemoryInfo(String name, String sessionId) {
        MemoryInfo memoryInfo = ServerInfoUtil.getMemoryInfo();
        return Result.success().add("infos", memoryInfo);
    }

    @GetMapping(Api.STATISTICS_CPU_INFO)
    @ApiOperation(value = "获取CPU信息")
    protected Result getCPUInfo(String name, String sessionId) {
        List<CPUInfo> cpuInfos = ServerInfoUtil.getCPUInfos();
        return Result.success().add("infos", cpuInfos);
    }


}
