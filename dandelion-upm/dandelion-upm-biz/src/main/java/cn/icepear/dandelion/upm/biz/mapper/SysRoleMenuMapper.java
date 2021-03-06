package cn.icepear.dandelion.upm.biz.mapper;


import cn.icepear.dandelion.upm.api.domain.entity.SysRoleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author rim-wood
 * @description 角色菜单管理 Mapper 接口
 * @date Created on 2019-04-18.
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 添加角色与菜单关联
     */
    int saveSysRoleMenu(List<SysRoleMenu> sysRoleMenus);
    
    /**
     * 按菜单id删除关联
     */
    void deleteByMenuId(@Param("menuId") Long menuId);

    /**
     * 使用角色id删除角色-菜单关联表数据
     */
    int deleteMenuByRoleId(@Param("roleId") Long roleId);
}
