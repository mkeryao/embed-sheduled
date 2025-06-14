/**
 * collapsible-sections.js
 * 实现表单可折叠部分的处理
 */

// 避免重复初始化
if (typeof window.collapsibleSectionsInitialized === 'undefined') {
    console.log('初始化可折叠部分脚本...');
    window.collapsibleSectionsInitialized = true;

    $(document).ready(function() {
        // 保存每个部分的折叠状态到本地存储
        $('.collapsible-header').on('click', function() {
            const targetId = $(this).data('target');
            const isExpanded = $(targetId).hasClass('show');
            
            // 更新图标
            const icon = $(this).find('i');
            icon.removeClass(isExpanded ? 'bi-chevron-down' : 'bi-chevron-right');
            icon.addClass(isExpanded ? 'bi-chevron-right' : 'bi-chevron-down');
            
            // 保存状态到本地存储
            try {
                localStorage.setItem(targetId, !isExpanded);
            } catch (e) {
                console.warn('无法保存部分折叠状态到本地存储:', e);
            }
            
            // 记录日志（仅在调试模式下）
            if (window.debugMode) {
                console.log('切换部分折叠状态:', targetId, '新状态:', !isExpanded ? '展开' : '折叠');
            }
        });

        // 初始化：从本地存储中恢复每个部分的折叠状态
        $('.collapsible-header').each(function() {
            try {
                const targetId = $(this).data('target');
                if (!targetId) return;
                
                const shouldCollapse = localStorage.getItem(targetId) === 'false';
                
                if (shouldCollapse) {
                    $(targetId).removeClass('show');
                    const icon = $(this).find('i');
                    icon.removeClass('bi-chevron-down').addClass('bi-chevron-right');
                }
            } catch (e) {
                console.warn('恢复折叠状态时出错:', e);
            }
        });
        
        // 为每个可折叠标题添加手型指针样式
        $('.collapsible-header').css('cursor', 'pointer');
        
        console.log('可折叠部分初始化完成');
    });
    
    // 在任务表单显示时重新应用折叠状态
    $('#taskFormModal').on('shown.bs.modal', function() {
        setTimeout(function() {
            $('.collapsible-header').each(function() {
                const targetId = $(this).data('target');
                const shouldCollapse = localStorage.getItem(targetId) === 'false';
                
                if (shouldCollapse) {
                    $(targetId).removeClass('show');
                    const icon = $(this).find('i');
                    icon.removeClass('bi-chevron-down').addClass('bi-chevron-right');
                } else {
                    $(targetId).addClass('show');
                    const icon = $(this).find('i');
                    icon.removeClass('bi-chevron-right').addClass('bi-chevron-down');
                }
            });
        }, 100);
    });
    
    // 双击标题区域可以快速展开当前部分并折叠其他部分
    $('.collapsible-header').on('dblclick', function(e) {
        e.stopPropagation(); // 防止触发单击事件
        
        const targetId = $(this).data('target');
        
        // 折叠所有其他部分
        $('.collapse.show').not(targetId).removeClass('show');
        $('.collapsible-header').not(this).find('i')
            .removeClass('bi-chevron-down')
            .addClass('bi-chevron-right');
        
        // 展开当前部分
        $(targetId).addClass('show');
        $(this).find('i')
            .removeClass('bi-chevron-right')
            .addClass('bi-chevron-down');
        
        // 更新本地存储
        $('.collapsible-header').each(function() {
            const id = $(this).data('target');
            localStorage.setItem(id, id === targetId);
        });
        
        console.log('双击展开:', targetId);
    });
}
