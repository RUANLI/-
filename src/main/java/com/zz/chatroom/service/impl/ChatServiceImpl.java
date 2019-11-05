package com.zz.chatroom.service.impl;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zz.chatroom.bean.GroupInfoBean;
import com.zz.chatroom.bean.MessagesBean;
import com.zz.chatroom.bean.UserInfoBean;
import com.zz.chatroom.dao.GroupInfoDao;
import com.zz.chatroom.dao.MessagesDao;
import com.zz.chatroom.dao.UserInfoDao;
import com.zz.chatroom.service.ChatService;
import com.zz.chatroom.util.ChatType;
import com.zz.chatroom.util.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import com.zz.chatroom.util.ResponseJson;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatServiceImpl.class);

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    @Autowired
    private GroupInfoDao groupInfoDao;

    @Autowired
    private UserInfoDao userInfoDao;

    @Autowired
    private MessagesDao messagesDao;

    //注册
    @Override
    public void register(JSONObject param, ChannelHandlerContext ctx) {
        String userId = param.get("userId").toString();
        Constant.onlineUserMap.put(userId, ctx);
        String responseJson = new ResponseJson().success()
                .setData("type", ChatType.REGISTER)
                .toString();
        sendMessage(ctx, responseJson);
        LOGGER.info(MessageFormat.format("userId为 {0} 的用户登记到在线用户表，当前在线人数为：{1}"
                , userId, Constant.onlineUserMap.size()));
    }

    //离线消息
    @Override
    public void offlineMessage(JSONObject param, ChannelHandlerContext ctx) {
        String userId = param.get("userId").toString();
        int id = Integer.parseInt(userId);
        UserInfoBean userInfoBean = userInfoDao.selectById(id);
        //获取离线时间
        Date OfflineTime = userInfoBean.getUserOfflineTime();
        QueryWrapper<MessagesBean> ew = new QueryWrapper<>();
        //获取离线消息
        ew.and(wrapper ->wrapper.eq("M_TO_USER_ID", id).or().in("M_GID",1) ).gt("M_TIME", OfflineTime).last("order By M_TIME Asc");
        List<MessagesBean> offlineMessages = messagesDao.selectList(ew);
        Iterator<MessagesBean> ms = offlineMessages.iterator();
        while (ms.hasNext()) {
            MessagesBean next = ms.next();
            switch (next.getType()) {
                case 1:
                    sendMessage(ctx, new ResponseJson().success()
                            .setData("content", next.getContent())
                            .setData("toUserId", next.getToUserId())
                            .setData("fromUserName", userInfoDao.selectById(next.getFromUserId()).getUserName())
                            .setData("fromUserId", next.getFromUserId())
                            .setData("sendTime", SDF.format(next.getUserTime()))
                            .setData("type", ChatType.SINGLE_SENDING)
                            .toString());
                    break;
                case 2:
                    sendMessage(ctx, new ResponseJson().success()
                            .setData("content", next.getContent())
                            .setData("fromUserName", userInfoDao.selectById(next.getFromUserId()).getUserName())
                            .setData("fromUserIcon", userInfoDao.selectById(next.getFromUserId()).getUserIcon())
                            .setData("fromUserId", next.getFromUserId())
                            .setData("sendTime", SDF.format(next.getUserTime()))
                            .setData("toGroupId", next.getGroupId())
                            .setData("type", ChatType.GROUP_SENDING)
                            .toString());
                    break;
                case 3:
                    sendMessage(ctx, new ResponseJson().success()
                            .setData("fromUserId", next.getFromUserId())
                            .setData("fromUserName", userInfoDao.selectById(next.getFromUserId()).getUserName())
                            .setData("originalFilename", next.getFileName())
                            .setData("fileSize", next.getFileSize())
                            .setData("sendTime", SDF.format(next.getUserTime()))
                            .setData("fileUrl", next.getFileUrl())
                            .setData("type", ChatType.FILE_MSG_SINGLE_SENDING)
                            .toString());
                    break;
                case 4:
                    sendMessage(ctx, new ResponseJson().success()
                            .setData("fromUserId", next.getFromUserId())
                            .setData("fromUserName", userInfoDao.selectById(next.getFromUserId()).getUserName())
                            .setData("fromUserIcon",userInfoDao.selectById(next.getFromUserId()).getUserIcon())
                            .setData("toGroupId", next.getGroupId())
                            .setData("originalFilename", next.getFileName())
                            .setData("fileSize", next.getFileSize())
                            .setData("sendTime", SDF.format(next.getUserTime()))
                            .setData("fileUrl", next.getFileUrl())
                            .setData("type", ChatType.FILE_MSG_GROUP_SENDING)
                            .toString());
                    break;
                default:
                    LOGGER.info(MessageFormat.format("userId为 {0} 有一条消息id为{1}的转化出错"
                            , userId, next.getMessageId()));
                    break;
            }
        }
    }

        //好友消息
        @Override
        public void singleSend (JSONObject param, ChannelHandlerContext ctx){
            String fromUserId = param.get("fromUserId").toString();
            String toUserId = param.get("toUserId").toString();
            String content = param.get("content").toString();
            //获取好友的客户端连接
            ChannelHandlerContext toUserCtx = Constant.onlineUserMap.get(toUserId);
            MessagesBean entity = new MessagesBean();
            //消息存入数据库
            entity.setContent(content)
                    .setFromUserId(Integer.parseInt(fromUserId))
                    .setToUserId(Integer.parseInt(toUserId))
                    .setUserTime(new Date())
                    .setType(1);
            messagesDao.insert(entity);
            if (null == toUserCtx) {
                String responseJson = new ResponseJson()
                        .error(MessageFormat.format("userId为 {0} 的用户没有登录！", toUserId))
                        .toString();
                sendMessage(ctx, responseJson);
            } else {
                String responseJson = new ResponseJson().success()
                        .setData("fromUserId", fromUserId)
                        .setData("fromUserName", userInfoDao.selectById(Integer.parseInt(fromUserId)).getUserName())
                        .setData("sendTime", SDF.format(new Date()))
                        .setData("content", content)
                        .setData("type", ChatType.SINGLE_SENDING)
                        .toString();
                sendMessage(toUserCtx, responseJson);
            }
        }

        //群消息
        @Override
        public void groupSend (JSONObject param, ChannelHandlerContext ctx){

            String fromUserId = param.get("fromUserId").toString();
            String toGroupId = param.get("toGroupId").toString();
            String content = param.get("content").toString();

            GroupInfoBean groupInfo = groupInfoDao.selectById(Integer.parseInt(toGroupId));
            QueryWrapper<UserInfoBean> ew = new QueryWrapper<>();
            groupInfo.setMembers(userInfoDao.selectList(ew));
            if (groupInfo == null) {
                String responseJson = new ResponseJson().error("该群id不存在").toString();
                sendMessage(ctx, responseJson);
            } else {
                UserInfoBean userInfoBean = userInfoDao.selectById(Integer.parseInt(fromUserId));
                String responseJson = new ResponseJson().success()
                        .setData("fromUserId", fromUserId)
                        .setData("fromUserName", userInfoBean.getUserName())
                        .setData("fromUserIcon", userInfoBean.getUserIcon())
                        .setData("content", content)
                        .setData("toGroupId", toGroupId)
                        .setData("type", ChatType.GROUP_SENDING)
                        .setData("sendTime", SDF.format(new Date()))
                        .toString();
                //将群消息存入数据库
                MessagesBean entity = new MessagesBean();
                entity.setGroupId(Integer.parseInt(toGroupId))
                        .setFromUserId(Integer.parseInt(fromUserId))
                        .setUserTime(new Date())
                        .setContent(content)
                        .setType(2);
                messagesDao.insert(entity);
                groupInfo.getMembers()
                        .forEach(member -> {
                            ChannelHandlerContext toCtx = Constant.onlineUserMap.get((member.getUserId()).toString());
                            if (null != toCtx && !((member.getUserId().toString()).equals(fromUserId))) {
                                sendMessage(toCtx, responseJson);
                            }
                        });
            }
        }

        //好友文件
        @Override
        public void FileMsgSingleSend (JSONObject param, ChannelHandlerContext ctx){
            String fromUserId = param.get("fromUserId").toString();
            String toUserId = param.get("toUserId").toString();
            String originalFilename = param.get("originalFilename").toString();
            String fileSize = param.get("fileSize").toString();
            String fileUrl = param.get("fileUrl").toString();
            ChannelHandlerContext toUserCtx = Constant.onlineUserMap.get(toUserId);
            if (toUserCtx == null) {
                String responseJson = new ResponseJson()
                        .error(MessageFormat.format("userId为 {0} 的用户没有登录！", toUserId))
                        .toString();
                //文件存入数据库
                MessagesBean entity = new MessagesBean();
                entity.setFromUserId(Integer.parseInt(fromUserId))
                        .setToUserId(Integer.parseInt(toUserId))
                        .setUserTime(new Date())
                        .setFileUrl(fileUrl)
                        .setFileName(originalFilename)
                        .setFileSize(fileSize)
                        .setType(3);
                messagesDao.insert(entity);
                sendMessage(ctx, responseJson);
            } else {
                String responseJson = new ResponseJson().success()
                        .setData("fromUserId", fromUserId)
                        .setData("originalFilename", originalFilename)
                        .setData("fromUserName",userInfoDao.selectById(Integer.parseInt(fromUserId)).getUserName())
                        .setData("fileSize", fileSize)
                        .setData("fileUrl", fileUrl)
                        .setData("sendTime", SDF.format(new Date()))
                        .setData("type", ChatType.FILE_MSG_SINGLE_SENDING)
                        .toString();
                sendMessage(toUserCtx, responseJson);
            }
        }

        //群文件
        @Override
        public void FileMsgGroupSend (JSONObject param, ChannelHandlerContext ctx){
            String fromUserId = param.get("fromUserId").toString();
            String toGroupId = param.get("toGroupId").toString();
            String originalFilename = param.get("originalFilename").toString();
            String fileSize = param.get("fileSize").toString();
            String fileUrl = param.get("fileUrl").toString();
            GroupInfoBean groupInfo = groupInfoDao.selectById(toGroupId);
            QueryWrapper<UserInfoBean> ew = new QueryWrapper<>();
            groupInfo.setMembers(userInfoDao.selectList(ew));
            if (groupInfo == null) {
                String responseJson = new ResponseJson().error("该群id不存在").toString();
                sendMessage(ctx, responseJson);
            } else {
                String responseJson = new ResponseJson().success()
                        .setData("fromUserId", fromUserId)
                        .setData("toGroupId", toGroupId)
                        .setData("fromUserName",userInfoDao.selectById(Integer.parseInt(fromUserId)).getUserName())
                        .setData("fromUserIcon",userInfoDao.selectById(Integer.parseInt(fromUserId)).getUserIcon())
                        .setData("originalFilename", originalFilename)
                        .setData("fileSize", fileSize)
                        .setData("sendTime", SDF.format(new Date()))
                        .setData("fileUrl", fileUrl)
                        .setData("type", ChatType.FILE_MSG_GROUP_SENDING)
                        .toString();

                //将群文件存入数据库
                MessagesBean entity = new MessagesBean();
                entity.setGroupId(Integer.parseInt(toGroupId))
                        .setFromUserId(Integer.parseInt(fromUserId))
                        .setUserTime(new Date())
                        .setFileUrl(fileUrl)
                        .setFileName(originalFilename)
                        .setFileSize(fileSize)
                        .setType(4);
                messagesDao.insert(entity);
                groupInfo.getMembers().forEach(member -> {
                            ChannelHandlerContext toCtx = Constant.onlineUserMap.get(member.getUserId().toString());
                            if (toCtx != null && !(member.getUserId().toString()).equals(fromUserId)) {
                                sendMessage(toCtx, responseJson);
                            }
                        });
            }
        }

        @Override
        public void remove (ChannelHandlerContext ctx){
            Iterator<Entry<String, ChannelHandlerContext>> iterator =
                    Constant.onlineUserMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<String, ChannelHandlerContext> entry = iterator.next();
                if (entry.getValue() == ctx) {
                    LOGGER.info("正在移除握手实例...");
                    Constant.webSocketHandshakerMap.remove(ctx.channel().id().asLongText());
                    LOGGER.info(MessageFormat.format("已移除握手实例，当前握手实例总数为：{0}"
                            , Constant.webSocketHandshakerMap.size()));
                    iterator.remove();
                    LOGGER.info(MessageFormat.format("userId为 {0} 的用户已退出聊天，当前在线人数为：{1}"
                            , entry.getKey(), Constant.onlineUserMap.size()));
                    UserInfoBean userInfo = userInfoDao.selectById(Integer.parseInt(entry.getKey()));
                    userInfo.setUserOfflineTime(new Date());
                    userInfoDao.updateById(userInfo);
                    break;
                }
            }
        }

        @Override
        public void typeError (ChannelHandlerContext ctx){
            String responseJson = new ResponseJson()
                    .error("该类型不存在！")
                    .toString();
            sendMessage(ctx, responseJson);
        }

        //消息发送
        @Override
        public  void sendMessage (ChannelHandlerContext ctx, String message){
            ctx.channel().writeAndFlush(new TextWebSocketFrame(message));
        }

    }