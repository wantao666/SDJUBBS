package com.selenium.sdjubbs.common.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.selenium.sdjubbs.common.api.Api;
import com.selenium.sdjubbs.common.bean.*;
import com.selenium.sdjubbs.common.service.*;
import com.selenium.sdjubbs.common.util.*;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * description:返回数据API
 * attention: 1.所有需要登录后才能操作的方法设为protected
 */
@RestController
@RequestMapping("/api")
@Slf4j
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class ApiController {
    @Autowired
    private UserService userService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private BlockService blockService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private ReplyService replyService;


    /**
     * method: post
     * url: /register
     * description: 注册
     */
    @PostMapping(Api.REGISTER)
    public Result register(@Valid User user, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            List<ObjectError> allErrors = bindingResult.getAllErrors();
            for (ObjectError error : allErrors) {
                return Result.failure(Constant.REGISTER_USER_FORMAT_ERROR_CODE, error.getDefaultMessage());
            }
        }
        //通过MD5+随机salt加密写入数据库的密码
        String salt = UUID.randomUUID().toString().substring(1, 10);
        user.setSalt(salt);
        String password = MD5Util.dbEncryption(user.getPassword(), salt);
        user.setPassword(password);
        //用户初始化操作
        user.setAge(0);
        user.setGender(2);
        user.setPhone("00000000000");
        user.setHeadPicture("/common/images/0.jpg");
        user.setRegisterTime(TimeUtil.getTime());
        user.setLastLoginTime(TimeUtil.getTime());
        user.setStatus(0);
        try {
            userService.addUser(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
        return Result.success()
                .add("username", user.getUsername())
                .add("email", user.getEmail());
    }

    /**
     * method: post
     * url: /login
     * description: 登录
     */
    @PostMapping(Api.LOGIN)
    public Result login(String username, String password, HttpSession session) {
        User user = userService.getUserByUsername(username);
        if (user != null) {
            if (user.getStatus() == 1) {
                return Result.failure(Constant.FAILURE_CODE, Constant.LOGIN_USER_FORBID_CODE, Constant.LOGIN_USER_FORBID);
            }
            String salt = user.getSalt();
            String realPassword = MD5Util.dbEncryption(password, salt);
            if (password != null && salt != null && realPassword.equals(user.getPassword())) {
                //存入redis key:username value:sessionId
                redisService.set(username, session.getId());
                return Result.success().add("username", username).add("sessionId", session.getId());
            } else {
                return Result.failure(Constant.FAILURE_CODE, Constant.LOGIN_USER_WRONG_PASSWORD_CODE, Constant.LOGIN_USER_WRONG_PASSWORD);
            }
        } else {
            return Result.failure(Constant.FAILURE_CODE, Constant.LOGIN_USER_NOT_EXIST_CODE, Constant.LOGIN_USER_NOT_EXIST);
        }
    }

    /**
     * method: post
     * url: /logout
     * description: 退出
     */
    @PostMapping(Api.LOGOUT)
    protected Result logout(String username, String sessionId) {
        if (redisService.delete(username)) {
            return Result.success();
        } else {
            return Result.failure();
        }
    }

    /**
     * method: get
     * url: /block
     * description: 获得所有板块
     */
    @GetMapping(Api.BLOCK)
    public Result getAllBlock() {
        List<Block> blocks = blockService.getAllBlock();
        return Result.success().add("blocks", blocks);
    }

    /**
     * method: get
     * url: /block/id
     * description: 根据板块id获取该板块下的所有文章
     */
    @GetMapping(Api.BLOCK + "/{id}")
    public Result getBlock(@PathVariable("id") int id, @RequestParam("pn") int pn) {
        Block block = blockService.getBlockById(id);
        PageHelper.startPage(pn, Constant.PAGE_SIZE);
        List<Article> articles = articleService.getAllArticleByBlockId(id);
        PageInfo<Article> pageInfo = new PageInfo<>(articles, Constant.NAVIGATE_PAGE_SIZE);
        return Result.success().add("block", block).add("pageInfo", pageInfo);
    }

    /**
     * method: get
     * url: /article/id
     * description: 根据文章id获取文章和文章下所有评论
     */
    @GetMapping(Api.ARTICLE + "/{id}")
    public Result getArticle(@PathVariable("id") int id) {
        Article article = articleService.getArticleById(id);
        List<Comment> comments = commentService.getCommentByArticleId(id);
        return Result.success().add("article", article).add("comments", comments);
    }

    /**
     * method: get
     * url: /comment/id
     * description: 根据评论id获取评论和评论下所有回复
     */
    @GetMapping(Api.COMMENT + "/{id}")
    public Result getComment(@PathVariable("id") int id) {
        Comment comment = commentService.getCommentById(id);
        List<Reply> replys = replyService.getReplyByCommentId(id);
        return Result.success().add("comment", comment).add("replys", replys);
    }

    /**
     * method: post
     * url: /comment
     * description: 增加一条评论
     */
    @PostMapping(Api.COMMENT)
    protected Result addComment(String username, String sessionId, Comment comment) {
        if (comment.getContent().trim() == null || comment.getContent().trim().length() == 0 || comment.getContent().trim().equals(" ")) {
            return Result.failure(Constant.USER_INPUT_EMPTY_CODE, Constant.USER_INPUT_EMPTY);
        }
        comment.setContent(HtmlUtil.htmlFilter(comment.getContent()));
        comment.setCreateTime(TimeUtil.getTime());
        comment.setUserId(userService.getUserByUsername(username).getId());
        log.info("userId" + userService.getUserByUsername(username).toString());
        commentService.addComment(comment);
        return Result.success().add("comment", comment);
    }

}
