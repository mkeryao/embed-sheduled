/**
 * workflow-task-selector.js
 * 工作流任务选择器增强脚本，解决图形模式下任务列表显示问题
 */

(function() {
    // 避免重复初始化
    if (typeof window.workflowTaskSelectorInitialized !== 'undefined') {
        console.log('工作流任务选择器脚本已初始化，跳过重复初始化');
        return;
    }

    console.log('初始化工作流任务选择器脚本...');
    window.workflowTaskSelectorInitialized = true;

    /**
     * 刷新工作流节点编辑器中的任务选择器
     * @param {string} selectedTaskId 可选，要选中的任务ID
     */
    window.refreshWorkflowNodeTaskSelector = function(selectedTaskId) {
        console.log('刷新工作流节点任务选择器，选中任务ID:', selectedTaskId || '无');
        
        // 查找当前模态框中的任务选择器
        const $nodeEditModal = $('#workflowNodeEditModal:visible, #nodeEditModal:visible');
        if ($nodeEditModal.length === 0) {
            console.log('未找到可见的节点编辑模态框，跳过刷新任务选择器');
            return;
        }
        
        // 查找模态框中的所有可能的选择器
        const $selectElements = $nodeEditModal.find('select');
        if ($selectElements.length === 0) {
            console.log('节点编辑模态框中未找到选择器');
            return;
        }
        
        // 查找最可能是任务选择器的元素
        let $taskSelector = null;
        
        // 按优先级查找
        const selectorIds = [
            '#availableTasksForNodes',
            '#taskConfig',
            '#nodeTaskConfig',
            '#selectTask',
            '#wfTaskSelect',
            '#taskSelector'
        ];
        
        // 首先通过ID查找
        for (const id of selectorIds) {
            const $select = $nodeEditModal.find(id);
            if ($select.length > 0) {
                $taskSelector = $select;
                console.log('通过ID找到任务选择器:', id);
                break;
            }
        }
        
        // 如果通过ID未找到，尝试通过类或属性查找
        if (!$taskSelector) {
            const $candidates = $nodeEditModal.find('select[id*="task"], select[id*="Task"], select.task-select, select[data-role="task-select"]');
            if ($candidates.length > 0) {
                $taskSelector = $candidates.first();
                console.log('通过类或属性找到任务选择器:', $taskSelector.attr('id') || '无ID');
            }
        }
        
        // 如果仍未找到，使用第一个选择器作为备选
        if (!$taskSelector && $selectElements.length > 0) {
            $taskSelector = $selectElements.first();
            console.log('使用第一个选择器作为备选:', $taskSelector.attr('id') || '无ID');
        }
        
        // 如果找到了选择器，加载任务列表
        if ($taskSelector) {
            // 保留当前选中的选项
            const currentValue = $taskSelector.val();
            const valueToSelect = selectedTaskId || currentValue || '';
            
            console.log('准备加载任务列表，当前值:', currentValue, '目标值:', valueToSelect);
            
            // 如果loadAvailableTasksForNodes函数存在，使用它加载任务
            if (typeof window.loadAvailableTasksForNodes === 'function') {
                // 在加载后选中指定的任务
                const originalLoadFunction = window.loadAvailableTasksForNodes;
                window.loadAvailableTasksForNodes = function() {
                    originalLoadFunction.apply(this, arguments);
                    
                    // 在任务加载完成后，选中指定的任务
                    setTimeout(function() {
                        if (valueToSelect) {
                            $taskSelector.val(valueToSelect).trigger('change');
                            
                            // 同步到其他可能的选择器
                            $nodeEditModal.find('select').not($taskSelector).each(function() {
                                const $this = $(this);
                                if ($this.find(`option[value="${valueToSelect}"]`).length > 0) {
                                    $this.val(valueToSelect).trigger('change');
                                }
                            });
                        }
                        
                        // 应用国际化
                        if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                            i18n.applyTranslations($nodeEditModal[0]);
                        }
                        
                        // 恢复原始函数
                        window.loadAvailableTasksForNodes = originalLoadFunction;
                    }, 200);
                };
                
                // 调用函数加载任务
                window.loadAvailableTasksForNodes();
            } else {
                console.warn('loadAvailableTasksForNodes函数不存在，无法加载任务列表');
            }
        } else {
            console.warn('未找到任何可用的任务选择器');
        }
    };
    
    /**
     * 在模态框显示时自动刷新任务选择器
     */
    function setupAutoRefresh() {
        // 监听节点编辑模态框的显示事件
        $('#workflowNodeEditModal, #nodeEditModal').on('shown.bs.modal', function() {
            console.log('节点编辑模态框显示，自动刷新任务选择器');
            window.refreshWorkflowNodeTaskSelector();
        });
        
        // 监听任务切换按钮点击事件
        $(document).on('click', '#refreshTaskListBtn, .refresh-tasks-btn', function() {
            console.log('刷新任务列表按钮点击');
            window.refreshWorkflowNodeTaskSelector();
        });
    }
    
    /**
     * 为节点编辑模态框添加刷新按钮
     */
    function addRefreshButton() {
        // 查找模态框中的任务选择器标签
        const $modals = $('#workflowNodeEditModal, #nodeEditModal');
        $modals.each(function() {
            const $modal = $(this);
            const $selects = $modal.find('select[id*="task"], select[id*="Task"], select.task-select, #availableTasksForNodes');
            
            $selects.each(function() {
                const $select = $(this);
                // 查找这个select的label
                const $formGroup = $select.closest('.form-group');
                const $label = $formGroup.find('label');
                
                if ($label.length > 0 && $formGroup.find('.refresh-tasks-btn').length === 0) {
                    // 在label后添加刷新按钮
                    $label.append(' <button type="button" class="btn btn-sm btn-outline-secondary refresh-tasks-btn" title="刷新任务列表"><i class="bi bi-arrow-clockwise"></i></button>');
                }
            });
        });
    }
    
    // 在文档加载完成后初始化
    $(document).ready(function() {
        setupAutoRefresh();
        
        // 使用MutationObserver监听DOM变化，以处理动态添加的模态框
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                    for (let i = 0; i < mutation.addedNodes.length; i++) {
                        const node = mutation.addedNodes[i];
                        if (node.nodeType === 1) {
                            // 检查是否是模态框或包含模态框
                            const $modal = $(node).find('.modal').addBack('.modal');
                            if ($modal.length > 0) {
                                // 为新添加的模态框添加刷新按钮
                                setTimeout(addRefreshButton, 100);
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
        
        // 初始化添加刷新按钮
        setTimeout(addRefreshButton, 500);
    });
})();
