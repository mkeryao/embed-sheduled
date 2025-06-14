/**
 * workflow-i18n.js
 * 工作流国际化相关功能
 */

(function() {
    // 避免重复初始化
    if (typeof window.workflowI18nInitialized !== 'undefined') {
        console.log('工作流国际化脚本已初始化，跳过重复初始化');
        return;
    }

    console.log('初始化工作流国际化脚本...');
    window.workflowI18nInitialized = true;

    /**
     * 应用国际化到工作流相关的模态框
     */
    function applyI18nToWorkflowModals() {
        console.log('应用国际化到工作流相关模态框...');
        
        // 工作流节点编辑模态框（支持多种可能的ID）
        $('#workflowNodeEditModal, #nodeEditModal, .node-edit-modal, [id*="nodeEdit"], [id*="NodeEdit"]').on('shown.bs.modal', function() {
            const $modal = $(this);
            console.log('节点编辑模态框显示:', $modal.attr('id'));
            
            // 查找任务选择下拉框
            const $taskSelects = $modal.find('select[id*="task"], select[id*="Task"], select.task-select, select[id="availableTasksForNodes"]');
            if ($taskSelects.length > 0) {
                console.log('找到任务选择下拉框:', $taskSelects.length, '个');
                
                // 检查是否为空，如果为空则加载任务
                if ($taskSelects.first().find('option').length <= 1) {
                    console.log('任务选择下拉框为空，正在加载任务...');
                    if (typeof window.loadAvailableTasksForNodes === 'function') {
                        window.loadAvailableTasksForNodes();
                    }
                }
            } else {
                console.log('未找到任务选择下拉框，将尝试其他通用选择器');
                const $anySelects = $modal.find('select');
                if ($anySelects.length > 0) {
                    console.log('找到下拉框:', $anySelects.length, '个，将尝试加载任务');
                    if (typeof window.loadAvailableTasksForNodes === 'function') {
                        window.loadAvailableTasksForNodes();
                    }
                }
            }
            
            // 立即应用翻译，而不使用setTimeout延迟
            if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                console.log('立即应用国际化到工作流节点编辑模态框');
                i18n.applyTranslations($modal[0]);
                
                // 确保翻译应用到新加载的选项上，通过延迟应用一次更新
                setTimeout(function() {
                    console.log('再次应用国际化检查新加载的选项');
                    i18n.applyTranslations($modal[0]);
                }, 300);
            }
        });
        
        // 工作流边缘编辑模态框
        $('#workflowEdgeEditModal, #edgeEditModal, .edge-edit-modal, [id*="edgeEdit"], [id*="EdgeEdit"]').on('shown.bs.modal', function() {
            const $modal = $(this);
            // 立即应用翻译
            if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                console.log('应用国际化到工作流边缘编辑模态框');
                i18n.applyTranslations($modal[0]);
            }
        });
        
        // 任何包含工作流相关内容的模态框
        $('.modal').on('shown.bs.modal', function() {
            const $this = $(this);
            
            // 检查是否包含工作流相关内容
            if ($this.find('[id*="workflow"], [id*="node"], [id*="edge"], [class*="workflow"], [class*="node"], [class*="edge"]').length > 0) {
                console.log('检测到可能的工作流相关模态框:', $this.attr('id'));
                // 立即应用翻译
                if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                    console.log('应用国际化到可能的工作流相关模态框');
                    i18n.applyTranslations($this[0]);
                }
            }
        });
    }

    /**
     * 文档就绪后初始化
     */
    $(document).ready(function() {
        applyI18nToWorkflowModals();
        
        // 监听DOM变化，处理动态添加的内容
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                    for (let i = 0; i < mutation.addedNodes.length; i++) {
                        const node = mutation.addedNodes[i];
                        if (node.nodeType === 1 && (
                            node.id && (node.id.indexOf('workflow') !== -1 || node.id.indexOf('node') !== -1 || node.id.indexOf('edge') !== -1) ||
                            node.className && (node.className.indexOf('workflow') !== -1 || node.className.indexOf('node') !== -1 || node.className.indexOf('edge') !== -1)
                        )) {
                            console.log('检测到动态添加的工作流相关元素:', node);
                            // 立即应用翻译
                            if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                                i18n.applyTranslations(node);
                            }
                        }
                    }
                }
            });
        });
        
        // 观察整个文档
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
        
        console.log('已设置工作流DOM变化监听器');
        
        // 预加载工作流任务数据，以备后续使用
        setTimeout(function() {
            if (typeof window.loadAvailableTasksForNodes === 'function') {
                console.log('预加载工作流任务数据');
                window.loadAvailableTasksForNodes();
            }
        }, 1000);
    });
})();
