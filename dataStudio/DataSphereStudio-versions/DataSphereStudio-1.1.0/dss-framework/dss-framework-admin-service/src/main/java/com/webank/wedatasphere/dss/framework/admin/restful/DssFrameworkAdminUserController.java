package com.webank.wedatasphere.dss.framework.admin.restful;


import com.webank.wedatasphere.dss.framework.admin.common.constant.UserConstants;
import com.webank.wedatasphere.dss.framework.admin.common.domain.Message;
import com.webank.wedatasphere.dss.framework.admin.common.domain.PasswordResult;
import com.webank.wedatasphere.dss.framework.admin.common.domain.TableDataInfo;
import com.webank.wedatasphere.dss.framework.admin.common.utils.PasswordUtils;
import com.webank.wedatasphere.dss.framework.admin.common.utils.StringUtils;
import com.webank.wedatasphere.dss.framework.admin.conf.AdminConf;
import com.webank.wedatasphere.dss.framework.admin.pojo.entity.DssAdminUser;
import com.webank.wedatasphere.dss.framework.admin.service.DssAdminUserService;
import com.webank.wedatasphere.dss.framework.admin.service.LdapService;
import com.webank.wedatasphere.dss.framework.admin.xml.DssUserMapper;
import com.webank.wedatasphere.dss.standard.app.sso.Workspace;
import com.webank.wedatasphere.dss.standard.common.exception.AppStandardWarnException;
import com.webank.wedatasphere.dss.standard.sso.utils.SSOHelper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.linkis.server.security.SecurityFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping(path = "/dss/framework/admin/user", produces = {"application/json"})
@RestController
public class DssFrameworkAdminUserController extends BaseController {
    @Resource
    private DssAdminUserService dssAdminUserService;
    @Autowired
    private LdapService ldapService;
    @Autowired
    DssUserMapper dssUserMapper;

    @RequestMapping(path = "list", method = RequestMethod.GET)
//    public TableDataInfo list(DssAdminUser user) {
    public TableDataInfo list(@RequestParam(value = "userName", required = false) String userName,
                              @RequestParam(value = "deptId", required = false) Long deptId,
                              @RequestParam(value = "phonenumber", required = false) String phonenumber,
                              @RequestParam(value = "beginTime", required = false) String beginTime,
                              @RequestParam(value = "endTime", required = false) String endTime) {
        DssAdminUser user = new DssAdminUser();
        user.setUserName(userName);
        user.setDeptId(deptId);
        user.setPhonenumber(phonenumber);
        Map<String, Object> params = new HashMap<>();
        params.put("beginTime", beginTime);
        params.put("endTime", endTime);
        user.setParams(params);
        startPage();
        List<DssAdminUser> userList = dssAdminUserService.selectUserList(user);
        return getDataTable(userList);
    }

    @RequestMapping(path = "add", method = RequestMethod.POST)
    public Message add(@Validated @RequestBody DssAdminUser user, HttpServletRequest req) {
        try {
            PasswordResult passwordResult = PasswordUtils.checkPwd(user.getPassword(), user);
            if (UserConstants.NOT_UNIQUE.equals(dssAdminUserService.checkUserNameUnique(user.getUserName()))) {
                return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
            } else if (user.getUserName().contains(UserConstants.SINGLE_SPACE)) {
                return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
            } else if (StringUtils.isNotEmpty(user.getPhonenumber())
                    && UserConstants.NOT_UNIQUE.equals(dssAdminUserService.checkPhoneUnique(user))) {
                return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
            } else if (StringUtils.isNotEmpty(user.getEmail())
                    && UserConstants.NOT_UNIQUE.equals(dssAdminUserService.checkEmailUnique(user))) {
                return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
            } else if (!PasswordResult.PASSWORD_RULE_PASS.equals(passwordResult)) {
                return Message.error().data("??????????????????:", passwordResult.getMessage());
            }
            boolean ldapExist = ldapService.exist(AdminConf.LDAP_ADMIN_NAME.getValue(), AdminConf.LDAP_ADMIN_PASS.getValue(), AdminConf.LDAP_URL.getValue(), AdminConf.LDAP_BASE_DN.getValue(), user.getUserName());
            if (ldapExist) {
                return Message.error().message("????????????'" + user.getUserName() + "'????????????????????????ldap?????????");
            }
            String pwd = user.getPassword();
            user.setPassword(DigestUtils.md5Hex(pwd));
            user.setCreateBy(SecurityFilter.getLoginUsername(req));
            int rows = dssAdminUserService.insertUser(user, getWorkspace(req));
            String userName = user.getUserName();
            ldapService.addUser(AdminConf.LDAP_ADMIN_NAME.getValue(), AdminConf.LDAP_ADMIN_PASS.getValue(), AdminConf.LDAP_URL.getValue(), AdminConf.LDAP_BASE_DN.getValue(), userName, pwd);
            return Message.ok().data("rows", rows).message("????????????");
        } catch (Exception exception) {
            return Message.error().data("rows", 0).message(exception.getMessage());
        }

    }

    private Workspace getWorkspace(HttpServletRequest req) {
        Workspace workspace = new Workspace();
        try {
            SSOHelper.addWorkspaceInfo(req, workspace);
        } catch (AppStandardWarnException ignored) {} // ignore it.
        return workspace;
    }


    @RequestMapping(path = "{id}", method = RequestMethod.GET)
    public Message getInfo(@PathVariable("id") Long userId) {
        return Message.ok().data("users", dssAdminUserService.selectUserById(userId));
    }

    @RequestMapping(path = "userInfo", method = RequestMethod.GET)
    public Message getLoginUserInfo(HttpServletRequest request) {
        String userName = SecurityFilter.getLoginUsername(request);
        return Message.ok().data("userInfo", dssAdminUserService.selectUserByName(userName));
    }


    @RequestMapping(path = "edit", method = RequestMethod.POST)
    public Message edit(@Validated @RequestBody DssAdminUser user, HttpServletRequest req) {
        if (StringUtils.isNotEmpty(user.getPhonenumber())
                && UserConstants.NOT_UNIQUE.equals(dssAdminUserService.checkPhoneUnique(user))) {
            return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
        } else if (StringUtils.isNotEmpty(user.getEmail())
                && UserConstants.NOT_UNIQUE.equals(dssAdminUserService.checkEmailUnique(user))) {
            return Message.error().message("????????????'" + user.getUserName() + "'??????????????????????????????");
        }
        return Message.ok().data("?????????????????????", dssAdminUserService.updateUser(user, getWorkspace(req)));
    }

}

