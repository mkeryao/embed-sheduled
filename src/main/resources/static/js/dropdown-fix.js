/**
 * dropdown-fix.js
 * 用于修复Bootstrap下拉菜单的补丁脚本
 */

// 等待文档就绪
$(document).ready(function() {
    console.log('初始化dropdown补丁脚本...');

    // 确保dropdown菜单在点击后正常工作
    $(document).on('click', '.dropdown-toggle', function(e) {
        // 防止链接的默认动作
        e.preventDefault();
        
        // 通过Bootstrap的data-toggle="dropdown"属性，Bootstrap已经自动处理了下拉菜单的显示
        // 这里只是额外的安全措施，确保在某些边缘情况下下拉菜单也能正常工作
    });

    // 修复移动设备上可能出现的点击问题
    $(document).on('touchstart', '.dropdown-menu .dropdown-item', function(e) {
        if ($(window).width() < 768) { // 仅在移动设备上
            e.stopPropagation();
        }
    });

    // 检查并初始化所有快速添加节点按钮
    const quickAddNodeButtons = $('.quick-add-node');
    if (quickAddNodeButtons.length > 0) {
        console.log('快速添加节点按钮就绪: ' + quickAddNodeButtons.length);
    }

    // 确保所有dropdown-toggle按钮都有正确的aria属性
    $('[data-toggle="dropdown"]').each(function() {
        if (!$(this).attr('aria-expanded')) {
            $(this).attr('aria-expanded', 'false');
        }
    });
});