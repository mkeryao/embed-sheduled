/**
 * tasks-ui.js
 * 任务管理UI交互功能，包括事件绑定、表单处理等
 */

// 避免重复初始化
if (typeof window.tasksUiInitialized === 'undefined') {
    console.log('初始化tasks UI脚本...');
    window.tasksUiInitialized = true;

    /**
     * 填充任务类型过滤器下拉框
     */
    window.populateTaskTypeFilter = function() {
        const taskTypes = [
            { id: '', name: i18n.translate('tasksPage.filters.allTypes', '所有类型') },
            { id: '0', name: i18n.translate('tasksPage.taskType.bean', 'Bean任务') },
            { id: '2', name: i18n.translate('tasksPage.taskType.http', 'HTTP任务') },
            { id: '4', name: i18n.translate('tasksPage.taskType.shell', 'Shell任务') },
            { id: '10', name: i18n.translate('tasksPage.taskType.workflow', '工作流任务') }
        ];

        const filterSelect = $('#filterTaskType');
        filterSelect.empty();

        // Add a placeholder option for calendar group with description
        const calendarGroupSelect = $('#calendarGroup');
        if (calendarGroupSelect.length > 0) {
            calendarGroupSelect.empty();
            calendarGroupSelect.append($('<option></option>').attr('value', '').text('-- 选择日历组 --'));
        }

        taskTypes.forEach(type => {
            filterSelect.append($('<option></option>')
                .attr('value', type.id)
                .text(type.name));
        });
    };


    /**
     * 增强的API错误处理
     * @param {Object} jqXHR - jQuery XHR对象
     * @param {string} textStatus - 状态文本
     * @param {string} errorThrown - 抛出的错误
     * @param {string} context - 错误上下文描述
     */
    window.handleApiError = function(jqXHR, textStatus, errorThrown, context) {
        let errorMessage = '';

        // 尝试从响应中获取详细错误信息
        if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
            errorMessage = jqXHR.responseJSON.message;
        } else if (jqXHR.responseText) {
            try {
                const resp = JSON.parse(jqXHR.responseText);
                if (resp.message) {
                    errorMessage = resp.message;
                }
            } catch (e) {
                // 不是JSON格式，使用原始响应文本
                if (jqXHR.responseText.length < 100) {
                    errorMessage = jqXHR.responseText;
                }
            }
        }

        // 如果没有提取到有意义的错误信息，使用状态代码和错误文本
        if (!errorMessage) {
            errorMessage = `${jqXHR.status}: ${errorThrown || textStatus}`;
        }

        // 记录详细错误到控制台
        console.error(`API错误 (${context}):`, {
            status: jqXHR.status,
            statusText: jqXHR.statusText,
            responseText: jqXHR.responseText,
            errorThrown: errorThrown,
            url: jqXHR.responseURL || '未知URL'
        });

        // 显示用户友好的错误提示
        showFeedback(`${context}: ${errorMessage}`, true);

        return errorMessage;
    };
    // 预加载任务配置列表并缓存，用于下拉菜单
    window.cachedTasks = [];
    $(document).ready(function() {
        console.log('预加载任务配置列表...');
        makeApiCall('GET', '/tasks', null,
            function(response) {
                console.log('成功获取任务配置，缓存 ' + response.length + ' 条记录');
                if (Array.isArray(response)) {
                    window.cachedTasks = response;
                }
            },
            function(jqXHR, textStatus, errorThrown) {
                window.handleApiError(jqXHR, textStatus, errorThrown, '预加载任务配置');
            }
        );
    });

    /**
     * 缩放DAG图的函数
     * @param {number} factor 缩放因子，大于1表示放大，小于1表示缩小
     */
    function zoomDag(factor) {
        try {
            // 确保currentScale已初始化
            if (typeof window.currentScale !== 'number') {
                window.currentScale = 1.0;
            }

            // 计算新的缩放比例
            const newScale = window.currentScale * factor;

            // 限制缩放范围，避免过大或过小
            if (newScale < 0.2) {
                console.log('已达到最小缩放比例');
                return;
            }
            if (newScale > 3.0) {
                console.log('已达到最大缩放比例');
                return;
            }

            // 更新全局缩放比例
            window.currentScale = newScale;

            // 应用新的缩放比例到SVG元素
            const svg = $('#dagContainer svg');
            if (svg.length) {
                svg.css('transform', `scale(${newScale})`);
                svg.css('transform-origin', 'top left');

                // 根据缩放更新视觉反馈
                updateZoomFeedback();

                console.log(`DAG图已缩放: ${Math.round(newScale * 100)}%`);
            } else {
                console.warn('找不到SVG元素，无法应用缩放');
            }
        } catch (e) {
            console.error('缩放DAG图时出错:', e);
        }
    }

    /**
     * 重置DAG图缩放到100%
     */
    function resetZoom() {
        try {
            // 重置全局缩放比例
            window.currentScale = 1.0;

            // 重置SVG元素的缩放
            const svg = $('#dagContainer svg');
            if (svg.length) {
                svg.css('transform', 'scale(1.0)');
                svg.css('transform-origin', 'top left');

                // 更新视觉反馈
                updateZoomFeedback();

                console.log('DAG图缩放已重置到100%');
            } else {
                console.warn('找不到SVG元素，无法重置缩放');
            }
        } catch (e) {
            console.error('重置DAG图缩放时出错:', e);
        }
    }

    /**
     * 更新缩放视觉反馈
     */
    function updateZoomFeedback() {
        try {
            // 显示当前缩放百分比
            const zoomPercentage = Math.round(window.currentScale * 100);
            $('#resetZoomBtn').text(`${zoomPercentage}%`);

            // 可选：根据缩放比例调整其他UI元素
            const dagContainer = $('#dagContainer');
            if (zoomPercentage > 100) {
                dagContainer.addClass('zoomed-in').removeClass('zoomed-out');
            } else if (zoomPercentage < 100) {
                dagContainer.addClass('zoomed-out').removeClass('zoomed-in');
            } else {
                dagContainer.removeClass('zoomed-in zoomed-out');
            }
        } catch (e) {
            console.error('更新缩放反馈时出错:', e);
        }
    }

    // 将函数暴露为全局函数，以便其他脚本可以调用
    window.zoomDag = zoomDag;
    window.resetZoom = resetZoom;

    /**
     * 确保任务表单模态窗口处于打开状态
     * 如果任务表单窗口不可见，则重新显示它
     */
    window.ensureTaskFormModalOpen = function() {
        if (!$('#taskFormModal').hasClass('show')) {
            console.log('确保任务表单窗口显示中');
            $('#taskFormModal').modal('show');
            return true;
        }
        return false;
    };    /**
     * 安全调用DAG重绘函数的辅助方法
     * 避免直接引用window.redrawDAG可能导致的未定义错误
     * 增强版本：增加重复调用保护和超时处理
     */
    window.safeRedrawDAG = function(enableDragging) {
        // 全局递归调用保护 - 使用递归调用计数器而不是简单标志位
        if (!window.redrawDAGCounter) {
            window.redrawDAGCounter = 0;
        }

        // 如果检测到过多的连续调用，强制冷却一段时间
        if (window.redrawDAGCounter > 2) {
            console.warn('检测到过多的连续redrawDAG调用，强制冷却5秒');
            if (!window.redrawDAGCooldown) {
                window.redrawDAGCooldown = true;
                setTimeout(function() {
                    console.log('redrawDAG冷却期结束，重置计数器');
                    window.redrawDAGCounter = 0;
                    window.redrawDAGCooldown = false;
                }, 5000);
            }
            return;
        }

        // 增加调用计数
        window.redrawDAGCounter++;

        // 如果当前正在重绘，防止递归调用
        if (window.isRedrawingDAG) {
            console.warn('避免递归调用redrawDAG - 当前已有重绘任务在进行中');
            return;
        }

        // 防止多个快速连续调用
        if (window.redrawDagTimer) {
            clearTimeout(window.redrawDagTimer);
            window.redrawDagTimer = null;
        }

        // 使用延时确保DOM更新完成并避免连续调用
        window.redrawDagTimer = setTimeout(function() {
            try {
                // 设置正在绘制的标志
                window.isRedrawingDAG = true;

                // 直接调用原始函数，增加错误处理
                if (typeof window.redrawDAG_backup === 'function') {
                    // 使用保存的备份函数
                    window.redrawDAG_backup(enableDragging);
                } else if (typeof window.redrawDAG === 'function') {
                    window.redrawDAG(enableDragging);
                } else {
                    console.error('redrawDAG函数未定义，请确保tasks-workflow.js已正确加载');

                    // 最后的备用实现 - 只清空容器并显示提示
                    const svgContainer = $('#dagContainer');
                    if (svgContainer.length) {
                        svgContainer.empty().html('<p class="text-muted small">图形绘制函数未加载，请刷新页面尝试。</p>');
                    }
                }
            } catch (e) {
                console.error('调用redrawDAG时出错:', e);
            } finally {
                // 清理标志位和减少计数
                window.isRedrawingDAG = false;
                window.redrawDagTimer = null;
                setTimeout(function() {
                    if (window.redrawDAGCounter > 0) {
                        window.redrawDAGCounter--;
                    }
                }, 500);            }
        }, 50); // 略微增加延迟，确保DOM更新完成
    };

    // 创建全局函数的备份，但确保不覆盖现有函数
    if (typeof window.redrawDAG === 'function' && typeof window.redrawDAG_backup === 'undefined') {
        window.redrawDAG_backup = window.redrawDAG;
    }

    /**
     * 确保节点助手可见性函数的应急方案
     * 如果tasks-workflow.js中的原始函数未定义，使用这个替代版本
     */
    if (typeof window.ensureNodeHelperVisible !== 'function') {
        window.ensureNodeHelperVisible = function(forceShow) {
            console.log('使用应急方案的ensureNodeHelperVisible函数', forceShow);
            // 显示工作流任务字段
            $('#workflowTaskFields').show();

            // 处理节点助手卡片
            const nodeHelper = $('#nodeHelperCard');
            if (nodeHelper.length) {
                nodeHelper.show();

                // 处理内容区域
                const nodeHelperContent = $('#nodeHelperContent');
                if (nodeHelperContent.length) {
                    if (forceShow || localStorage.getItem('nodeHelperHidden') !== 'true') {
                        nodeHelperContent.show();
                    } else {
                        nodeHelperContent.hide();
                    }
                }
            }
        };
    }

    // 当页面DOM完全加载后执行
    $(document).ready(function() {
        // 确保缩放比例已初始化
        if (typeof window.currentScale !== 'number') {
            window.currentScale = 1.0;
        }

        // 初始化缩放显示
        if (typeof window.updateZoomFeedback === 'function') {
            window.updateZoomFeedback();
        } else {
            $('#resetZoomBtn').text('100%');
        }

        // 加载导航栏
        $("#navbar-container").load("_navbar.html", function() {
            const currentUsername = localStorage.getItem('username');
            if (currentUsername) {
                $('#loggedInUser').text(currentUsername);
            }
            $('#logout-link').on('click', function(e) {
                e.preventDefault();
                logout();
            });
        });

        // 确保模态窗口可以正确滚动
        $('#taskFormModal').on('shown.bs.modal', function() {
            // 重置模态窗口滚动条位置到顶部
            $(this).find('.modal-body').scrollTop(0);

            // 确保高级设置部分可以通过滚动到达
            setTimeout(function() {
                // 延迟处理，确保模态窗口已完全渲染
                const modalBody = $('.modal-body');
                if (modalBody.prop('scrollHeight') > modalBody.height()) {
                    // 如果内容高度大于容器，确保滚动样式已正确应用
                    modalBody.css('overflow-y', 'auto');
                }

                // 确保表单字段和标签完全显示，不被截断
                $('.form-group label').css({
                    'display': 'block',
                    'white-space': 'normal',
                    'overflow': 'visible'
                });

                // 修复任务表单中可能的显示问题
                adjustFormElementsVisibility();
            }, 100);
        });

        // 确保工作流节点编辑模态窗口也能正确滚动
        $('#workflowNodeEditModal').on('shown.bs.modal', function() {
            $(this).find('.modal-body').scrollTop(0);
            setTimeout(function() {
                const nodeModalBody = $('#workflowNodeEditModal .modal-body');
                if (nodeModalBody.prop('scrollHeight') > nodeModalBody.height()) {
                    nodeModalBody.css('overflow-y', 'auto');
                }

                // 确保表单元素完全显示
                adjustFormElementsVisibility();
            }, 100);
        });

        // 确保边缘属性模态窗口也能正确滚动
        $('#edgePropertyModal').on('shown.bs.modal', function() {
            $(this).find('.modal-body').scrollTop(0);
            setTimeout(function() {
                const edgeModalBody = $('#edgePropertyModal .modal-body');
                if (edgeModalBody.prop('scrollHeight') > edgeModalBody.height()) {
                    edgeModalBody.css('overflow-y', 'auto');
                }

                // 确保表单元素完全显示
                adjustFormElementsVisibility();
            }, 100);
        });

        // 帮助函数：调整表单元素可见性和样式
        function adjustFormElementsVisibility() {
            // 确保标签和输入字段完全可见
            $('.form-row .form-group').each(function() {
                $(this).css({
                    'min-width': '200px',
                    'width': '100%',
                    'padding': '0 10px'
                });

                // 确保输入字段完全显示
                $(this).find('input, select, textarea').css({
                    'width': '100%',
                    'display': 'block',
                    'box-sizing': 'border-box'
                });
            });

            // 特别处理Bean任务字段和方法名称字段，确保它们完全可见
            $('#beanName, #methodName').css({
                'width': '100%'
            });

            // 重新调整form-row的布局以确保良好的响应式行为
            $('.form-row').css({
                'display': 'flex',
                'flex-wrap': 'wrap',
                'margin-right': '-10px',
                'margin-left': '-10px'
            });
        }

        // 使用全局初始化函数
        if (typeof window.initializeI18n === 'function') {
            try {
                window.initializeI18n();
                console.log("i18n initialized for tasks.html");
                populateTaskTypeFilter();

                // 初始化完成后加载页面数据
                loadTasks();
                loadUsersForSelects();
                loadCalendarsForSelect();
            } catch (e) {
                console.error("Error initializing i18n for tasks.html", e);
            }
        } else {
            console.error("全局i18n初始化函数未定义，尝试使用传统方式初始化");
            // 回退到直接调用i18n.init
            if (typeof i18n !== 'undefined') {
                try {
                    i18n.init();
                    populateTaskTypeFilter();
                    loadTasks();
                    loadUsersForSelects();
                    loadCalendarsForSelect();
                } catch(e ) {
                     console.error("Error initializing i18n for tasks.html", e);
                }
            } else {
                console.error("i18n对象未定义，无法初始化国际化")
            }

            $("#navbar-container").load("_navbar.html", function () {
                const currentUsername = localStorage.getItem('username');
                if (currentUsername) {
                    $('#loggedInUser').text(currentUsername);
                }
                $('#logout-link').on('click', function (e) {
                    e.preventDefault();
                    logout();
                });
                if (typeof i18n !== 'undefined') {
                    i18n.applyTranslations(this); // For the navbar
                }
            });
        }

        // 添加任务表单提交处理
        $('#task-form').submit(function (event) {
            event.preventDefault();
            const taskId = $('#taskId').val();

            // Get selected users for notifications
            const successUsers = $('#notifySuccess').val() || [];
            const failedUsers = $('#notifyFailed').val() || [];

            const taskData = {
                taskName: $('#taskName').val(),
                cronExpression: $('#cronExpression').val(),
                description: $('#description').val(),
                executionMode: $('#executionMode').val(),
                taskType: parseInt($('#taskType').val()),
                active: $('#active').is(':checked'),
                beanName: $('#beanName').val(),
                methodName: $('#methodName').val(),
                startDate: $('#startDate').val() ? ($('#startDate').val() + ":00") : null,
                endDate: $('#endDate').val() ? ($('#endDate').val() + ":00") : null,
                taskCalendarGroup: $('#calendarGroup').val() || null,
                taskExcludeTimes: $('#excludeTimes').val() || null,
                executeTimeoutSeconds: parseInt($('#taskTimeout').val()) || 0,
                notifySuccessUserIds: successUsers.length > 0 ? successUsers.join(',') : null,
                notifyFailedUserIds: failedUsers.length > 0 ? failedUsers.join(',') : null,
                workflowNodes: null,
                workflowEdges: null,
                globalParameters: null,
                // 添加重试设置字段
                maxRetryAttempts: parseInt($('#maxRetries').val()) || 0,
                retryIntervalSeconds: parseInt($('#retryInterval').val()) || 30,
                retryIntervalMultiplier: parseFloat($('#retryMultiplier').val()) || 1.0
            };

            if (taskData.taskType === 0) {
                taskData.parameters = $('#parameters').val();
            } else if (taskData.taskType === 4) {
                const shellParams = {
                    script: $('#shellScriptContent').val(),
                    isInlineScript: $('#shellIsInlineScript').is(':checked'),
                    arguments: ($('#shellArguments').val() || '').split('\n').map(arg => arg.trim()).filter(arg => arg.length > 0),
                    workingDirectory: $('#shellWorkingDirectory').val() || null
                };
                taskData.parameters = JSON.stringify(shellParams);
                taskData.beanName = null;
                taskData.methodName = null;
            } else if (taskData.taskType === 2) {
                const httpParams = {
                    url: $('#httpUrl').val(),
                    method: $('#httpMethod').val(),
                    headers: parseJsonOrNull($('#httpHeaders').val()) || {},
                    body: $('#httpBody').val() || null,
                    connectTimeout: parseInt($('#httpConnectTimeout').val()) || 5000,
                    readTimeout: parseInt($('#httpReadTimeout').val()) || 30000,
                    retryCount: parseInt($('#httpRetryCount').val()) || 0,
                    successCode: $('#httpSuccessCode').val() || '200'
                };
                taskData.parameters = JSON.stringify(httpParams);
                taskData.beanName = null;
                taskData.methodName = null;
            } else if (taskData.taskType === 10) {
                console.log("保存工作流任务，类型ID: 10");

                // 确保工作流处于安全状态
                if (typeof window.checkWorkflowSafetyState === 'function') {
                    window.checkWorkflowSafetyState();
                }

                // 确保所有工作流位置变更都被应用到JSON
                if (typeof window.ensureWorkflowChangesSaved === 'function') {
                    console.log("执行工作流保存前检查");
                    const saveResult = window.ensureWorkflowChangesSaved();
                    if (saveResult === false) {
                        // ensureWorkflowChangesSaved 返回 false 表示有错误发生
                        console.error("工作流保存预处理失败，停止提交");
                        showFeedback(i18n.translate('tasksPage.feedback.workflowPreSaveFailed', '工作流保存前检查失败，请修正错误后再尝试保存'), true);
                        return; // 停止保存操作
                    }
                }

                try {
                    // 获取并解析工作流数据
                    const nodesJson = $('#workflowNodesJson').val();
                    const edgesJson = $('#workflowEdgesJson').val();
                    const globalParamsJson = $('#globalParametersJson').val();

                    taskData.workflowNodes = parseJsonOrNull(nodesJson);
                    taskData.workflowEdges = parseJsonOrNull(edgesJson);
                    taskData.globalParameters = parseJsonOrNull(globalParamsJson);

                    // 额外验证
                    if (!taskData.workflowNodes || !Array.isArray(taskData.workflowNodes) || taskData.workflowNodes.length === 0) {
                        showFeedback(i18n.translate('tasksPage.feedback.nodesRequired', '工作流必须包含至少一个节点'), true);
                        return;
                    }

                    console.log(`工作流数据已准备好保存: ${taskData.workflowNodes.length} 个节点, ${taskData.workflowEdges ? taskData.workflowEdges.length : 0} 条边`);
                } catch (e) {
                    console.error("解析工作流数据时出错:", e);
                    showFeedback(i18n.translate('tasksPage.feedback.workflowParseError', '解析工作流数据时出错: ') + e.message, true);
                    return;
                }

                taskData.beanName = null;
                taskData.methodName = null;
                taskData.parameters = null;

                // 检查节点和边缘数据是否有效
                if (!taskData.workflowNodes || taskData.workflowNodes.length === 0) {
                    console.warn("工作流节点数据为空");
                }

                console.log("准备保存工作流数据:",
                    "节点数:", taskData.workflowNodes ? taskData.workflowNodes.length : 0,
                    "边数:", taskData.workflowEdges ? taskData.workflowEdges.length : 0);
            }

            const method = taskId ? 'PUT' : 'POST';
            const endpoint = taskId ? `/tasks/${taskId}` : '/tasks';

            makeApiCall(method, endpoint, taskData,
                function (response) {
                    $('#taskFormModal').modal('hide');

                    // 根据任务类型提供更具体的成功消息
                    let successMsg = '';
                    if (taskData.taskType === 10) {
                        successMsg = i18n.translate('tasksPage.feedback.workflowSaved', '工作流任务已成功保存!');

                        // 获取包含信息的成功消息
                        const workflowDetails = `ID: ${response.taskId}, 名称: ${response.taskName}`;
                        console.log(`工作流保存成功! ${workflowDetails}`);

                        // 清除状态，防止再次编辑时出现问题
                        window.nodePositionChanged = false;

                        // 提供更详细的成功反馈
                        successMsg += ` (${workflowDetails})`;
                    } else {
                        successMsg = i18n.translate('tasksPage.feedback.taskSaved', 'Task saved successfully!');
                    }

                    showFeedback(successMsg, false);

                    // 重新加载任务列表
                    loadTasks();

                    // 确保所有临时状态被清除
                    if (taskData.taskType === 10 && typeof window.checkWorkflowSafetyState === 'function') {
                        setTimeout(function() {
                            window.checkWorkflowSafetyState();
                        }, 500);
                    }
                },
                function (jqXHR) {
                    // 提供更详细的错误信息
                    let errorDetails;
                    if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
                        errorDetails = jqXHR.responseJSON.message;
                    } else if (jqXHR.responseText) {
                        try {
                            const err = JSON.parse(jqXHR.responseText);
                            errorDetails = err.message || jqXHR.responseText;
                        } catch (e) {
                            errorDetails = jqXHR.responseText;
                        }
                    } else {
                        errorDetails = jqXHR.statusText;
                    }

                    let errorMsg = '';

                    if (taskData.taskType === 10) {
                        errorMsg = i18n.translate('tasksPage.feedback.errorSavingWorkflow', "保存工作流任务失败: {{error}}").replace("{{error}}", errorDetails);

                        console.error("工作流保存失败:", jqXHR);

                        // 如果错误与工作流节点或边缘有关，提供更明确的提示
                        if (errorDetails && errorDetails.toLowerCase().includes('nodes')) {
                            errorMsg += '\n' + i18n.translate('tasksPage.feedback.checkNodesJson', "请检查工作流节点配置是否正确");
                        }
                        if (errorDetails && errorDetails.toLowerCase().includes('edges')) {
                            errorMsg += '\n' + i18n.translate('tasksPage.feedback.checkEdgesJson', "请检查工作流边缘配置是否正确");
                        }

                        // 尝试恢复到安全状态
                        setTimeout(function() {
                            if (typeof window.checkWorkflowSafetyState === 'function') {
                                window.checkWorkflowSafetyState();
                            }
                        }, 500);
                    } else {
                        errorMsg = i18n.translate('tasksPage.feedback.errorSaving', "Error saving task: {{error}}").replace("{{error}}", errorDetails);
                    }

                    showFeedback(errorMsg, true);
                }
            );
        });

        // 保存任务
        $('#saveTaskBtn').click(function() {
            const taskId = $('#taskId').val();
            const taskType = parseInt($('#taskType').val(), 10);
            const cronExpression = $('#cronExpression').val();

            // For non-workflow tasks, cron is optional. For workflows (type 10), it's required.
            if (taskType === 10 && !cronExpression) {
                showFeedback('CRON expression is required for workflow tasks.', true);
                return;
            }

            // If cron is provided for any task type, it must be valid.
            if (cronExpression && !isValidCron(cronExpression)) {
                showFeedback('Invalid CRON expression format.', true);
                return;
            }

            // 收集表单数据
            const taskData = collectTaskData();
            if (!taskData) return; // 如果数据收集失败，则中止

            const method = taskId ? 'PUT' : 'POST';
            const url = taskId ? `/tasks/${taskId}` : '/tasks';

            makeApiCall(method, url, taskData,
                function(response) {
                    $('#taskFormModal').modal('hide');
                    showFeedback('Task saved successfully!', false);
                    loadTasks();
                },
                function(jqXHR, textStatus, errorThrown) {
                    handleApiError(jqXHR, textStatus, errorThrown, 'saving task');
                }
            );
        });

        // 任务类型变更处理
        $('#taskType').change(function () {
            const type = $(this).val();
            $('.type-specific-fields').hide();
            $('#beanName, #methodName, #parameters').closest('.form-group').show();

            const initialDagMsg = i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerPlaceholderInitial', "Define nodes and edges in the JSON textareas and click \"Refresh View\".");
            $('#dagContainer').empty().html(`<p class="text-muted small">${initialDagMsg}</p>`);

            if (type === '0') {
                $('#beanTaskFields').show();
            } else if (type === '4') {
                $('#shellTaskFields').show();
                $('#beanName, #methodName, #parameters').closest('.form-group').hide();
            } else if (type === '2') {
                $('#httpTaskFields').show();
                $('#beanName, #methodName, #parameters').closest('.form-group').hide();
            } else if (type === '10') {
                $('#workflowTaskFields').show();
                $('#beanName, #methodName, #parameters').closest('.form-group').hide();
                loadAvailableTasksForNodes();
                safeRedrawDAG();
            }

            // 确保国际化应用到新显示的字段
            setTimeout(() => {
                i18n.applyTranslations();
                console.log("已在任务类型切换后重新应用国际化");
            }, 100);
        }).trigger('change');        // 刷新DAG视图
        $('#refreshDagViewBtn').on('click', function () {
            safeRedrawDAG();
        });

        // 编辑按钮点击事件
        $('#tasks-table-body').on('click', '.edit-btn', function () {
            const taskId = $(this).data('id');

            // 清除表单
            $('#task-form')[0].reset();
            $('#taskId').val(taskId);

            // 获取任务详情并填充表单
            makeApiCall('GET', `/tasks/${taskId}`, null,
                function (task) {
                    $('#taskName').val(task.taskName);
                    $('#taskType').val(task.taskType.toString()).trigger('change');
                    $('#cronExpression').val(task.cronExpression);
                    $('#description').val(task.description);
                    $('#executionMode').val(task.executionMode || 'BROADCAST');
                    $('#active').prop('checked', task.active);

                    // Set startDate and endDate
                    if (task.startDate) {
                        // Remove seconds and timezone part if present
                        const startDate = task.startDate.replace(/:\d\d(\.\d+)?([+-]\d{2}:?\d{2}|Z)?$/, '');
                        $('#startDate').val(startDate);
                    }
                    if (task.endDate) {
                        // Remove seconds and timezone part if present
                        const endDate = task.endDate.replace(/:\d\d(\.\d+)?([+-]\d{2}:?\d{2}|Z)?$/, '');
                        $('#endDate').val(endDate);
                    }

                    // 处理特定类型任务的额外字段
                    if (task.taskType === 0) { // Bean Task
                        $('#beanName').val(task.beanName);
                        $('#methodName').val(task.methodName);
                        if (task.parameters) {
                            try {
                                const params = typeof task.parameters === 'string' ?
                                    JSON.parse(task.parameters) : task.parameters;
                                $('#parameters').val(JSON.stringify(params, null, 2));
                            } catch (e) {
                                $('#parameters').val(task.parameters);
                                console.warn('Failed to parse bean parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 2) { // HTTP Task
                        if (task.parameters) {
                            try {
                                const httpParams = typeof task.parameters === 'string' ?
                                    JSON.parse(task.parameters) : task.parameters;
                                $('#httpUrl').val(httpParams.url || '');
                                $('#httpMethod').val(httpParams.method || 'GET');
                                $('#httpHeaders').val(httpParams.headers ? JSON.stringify(httpParams.headers, null, 2) : '{}');
                                $('#httpBody').val(httpParams.body || '');
                                $('#httpConnectTimeout').val(httpParams.connectTimeout || 5000);
                                $('#httpReadTimeout').val(httpParams.readTimeout || 30000);
                                $('#httpRetryCount').val(httpParams.retryCount || 0);
                                $('#httpSuccessCode').val(httpParams.successCode || '200');
                            } catch (e) {
                                console.warn('Failed to parse HTTP task parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 4) { // Shell Task
                        if (task.parameters) {
                            try {
                                const shellParams = typeof task.parameters === 'string' ?
                                    JSON.parse(task.parameters) : task.parameters;
                                $('#shellScriptContent').val(shellParams.script || '');
                                $('#shellIsInlineScript').prop('checked', !!shellParams.isInlineScript);
                                $('#shellWorkingDirectory').val(shellParams.workingDirectory || '');
                                if (shellParams.arguments && Array.isArray(shellParams.arguments)) {
                                    $('#shellArguments').val(shellParams.arguments.join('\n'));
                                }
                            } catch (e) {
                                console.warn('Failed to parse Shell task parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 10) { // Workflow Task
                        // Initialize workflow data
                        const workflowNodes = task.workflowNodes || [];
                        const workflowEdges = task.workflowEdges || [];
                        const globalParams = task.globalParameters || {};

                        // Update JSON textareas
                        $('#workflowNodesJson').val(JSON.stringify(workflowNodes, null, 2));
                        $('#workflowEdgesJson').val(JSON.stringify(workflowEdges, null, 2));
                        $('#globalParametersJson').val(JSON.stringify(globalParams, null, 2));

                        // Initialize NodeManager with the workflow data
                        if (window.NodeManager && typeof window.NodeManager.initializeEditor === 'function') {
                            window.NodeManager.initializeEditor('dagContainer', workflowNodes, workflowEdges);
                        } else {
                            console.error('NodeManager not available for workflow initialization');
                        }

                        // Show workflow task fields
                        $('#workflowTaskFields').show();

                        // Load available tasks for nodes
                        loadAvailableTasksForNodes();

                        // Redraw DAG after setting the values
                        setTimeout(() => {
                            if (typeof safeRedrawDAG === 'function') {
                                safeRedrawDAG(true); // Enable dragging for workflow nodes
                            }
                        }, 100);
                    }

                    // 日期时间处理
                    $('#calendarGroup').val(task.taskCalendarGroup || '');
                    $('#excludeTimes').val(task.taskExcludeTimes || '');
                    $('#taskTimeout').val(task.executeTimeoutSeconds != null ? task.executeTimeoutSeconds : 0);

                    // 加载重试配置
                    $('#maxRetries').val(task.maxRetryAttempts != null ? task.maxRetryAttempts : 0);
                    $('#retryInterval').val(task.retryIntervalSeconds != null ? task.retryIntervalSeconds : 30);
                    $('#retryMultiplier').val(task.retryIntervalMultiplier != null ? task.retryIntervalMultiplier : 1.0);

                    const successUserIds = task.notifySuccessUserIds ? task.notifySuccessUserIds.split(',') : [];
                    $('#notifySuccess').val(successUserIds);
                    const failedUserIds = task.notifyFailedUserIds ? task.notifyFailedUserIds.split(',') : [];
                    $('#notifyFailed').val(failedUserIds);

                    $('#taskFormModal').modal('show');
                },
                function (jqXHR) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorLoadingTaskDetails', "Error fetching task details: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                }
            );
        });

        // 删除按钮点击事件，显示确认模态框
        $('#tasks-table-body').on('click', '.delete-btn', function () {
            var $row = $(this).closest('tr');
            var taskName = $row.find('td').eq(1).text().trim(); // 任务名称在第2列（索引为1）
            var taskId = $(this).data('id');



            $('#deleteTaskId').val(taskId);
            $('#deleteTaskNameInput').val('');
            $('#confirmDeleteBtn').prop('disabled', true);
            $('#deleteValidationMessage').hide();

            $('#deleteTaskNameInput').attr('data-i18n-key-placeholder',  taskName);
            $('#deleteTaskNameInput').attr('placeholder', taskName);
            // 确保国际化应用到模态框
            i18n.applyTranslations();

            $('#deleteConfirmModal').modal('show');
        });

        // 确认删除按钮点击事件
        $('#confirmDeleteBtn').off('click').on('click', function() {
            var inputName = $('#deleteTaskNameInput').val().trim();
            var taskId = $('#deleteTaskId').val();
            var expectedName = $('#deleteTaskNameInput').attr('placeholder');

            if (inputName === expectedName) {
                // 通过API删除任务
                makeApiCall('DELETE', `/tasks/${taskId}`, null,
                    function () {
                        $('#deleteConfirmModal').modal('hide');
                        showFeedback(i18n.translate('tasksPage.feedback.taskDeleted', "Task deleted successfully"), false);
                        loadTasks();
                    },
                    function (jqXHR) {
                        showFeedback(i18n.translate('tasksPage.feedback.errorDeleting', "Error deleting task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                    }
                );
            } else {
                $('#deleteValidationMessage').text(i18n.translate('tasksPage.deleteConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
            }
        });

        // 输入框内容变化时验证任务名称（删除确认）
        $('#deleteTaskNameInput').off('input').on('input', function () {
            var inputName = $(this).val().trim();
            var expectedName = $(this).attr('placeholder');

            if (inputName === expectedName) {
                $('#confirmDeleteBtn').prop('disabled', false);
                $('#deleteValidationMessage').hide();
            } else {
                $('#confirmDeleteBtn').prop('disabled', true);
                if (inputName && inputName.length > 0) {
                    $('#deleteValidationMessage').text(i18n.translate('tasksPage.deleteConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
                } else {
                    $('#deleteValidationMessage').hide();
                }
            }
        });

        // 监听Enter键在输入框中的按下事件（删除确认）
        $('#deleteTaskNameInput').keypress(function(e) {
            if (e.which === 13 && !$('#confirmDeleteBtn').prop('disabled')) {
                $('#confirmDeleteBtn').click();
            }
        });

        // 触发按钮点击事件，显示确认模态框
        $('#tasks-table-body').on('click', '.trigger-btn', function () {
            var $row = $(this).closest('tr');
            var taskName = $row.find('td').eq(1).text().trim(); // 任务名称在第2列（索引为1）
            var taskId = $(this).data('id');
            $('#manualTriggerTaskId').val(taskId);
            $('#manualTriggerTaskNameInput').val('');
            $('#confirmManualTriggerBtn').prop('disabled', true);
            $('#manualTriggerValidationMessage').hide();
            // 设置placeholder为任务名称
            $('#manualTriggerTaskNameInput').attr('placeholder', taskName);
            $('#manualTriggerTaskNameInput').attr('data-i18n-key-placeholder',  taskName);

            // 确保国际化应用到模态框
            i18n.applyTranslations();

            $('#manualTriggerConfirmModal').modal('show');
        });

        // 确认触发按钮点击事件
        $('#confirmManualTriggerBtn').off('click').on('click', function() {
            var inputName = $('#manualTriggerTaskNameInput').val().trim();
            var taskId = $('#manualTriggerTaskId').val();
            var expectedName = $('#manualTriggerTaskNameInput').attr('placeholder');

            if (inputName === expectedName) {
                // 通过API触发任务
                makeApiCall('POST', `/tasks/${taskId}/trigger`, null,
                    function () {
                        $('#manualTriggerConfirmModal').modal('hide');
                        showFeedback(i18n.translate('tasksPage.feedback.taskTriggered', "Task {{taskId}} triggered successfully!").replace('{{taskId}}', taskId), false);
                    },
                    function (jqXHR) {
                        showFeedback(i18n.translate('tasksPage.feedback.errorTriggering', "Error triggering task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                    }
                );
            } else {
                $('#manualTriggerValidationMessage').text(i18n.translate('tasksPage.manualTriggerConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
            }
        });

        // 输入框内容变化时验证任务名称
        $('#manualTriggerTaskNameInput').off('input').on('input', function () {
            var inputName = $(this).val().trim();
            var expectedName = $(this).attr('placeholder');

            if (inputName === expectedName) {
                $('#confirmManualTriggerBtn').prop('disabled', false);
                $('#manualTriggerValidationMessage').hide();
            } else {
                $('#confirmManualTriggerBtn').prop('disabled', true);
                if (inputName && inputName.length > 0) {
                    $('#manualTriggerValidationMessage').text(i18n.translate('tasksPage.manualTriggerConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
                } else {
                    $('#manualTriggerValidationMessage').hide();
                }
            }
        });

        // 监听Enter键在输入框中的按下事件
        $('#manualTriggerTaskNameInput').keypress(function(e) {
            if (e.which === 13 && !$('#confirmManualTriggerBtn').prop('disabled')) {
                // 如果按下Enter键并且确认按钮已启用，则触发确认按钮的点击事件
                $('#confirmManualTriggerBtn').click();
            }
        });

        // 启用按钮点击事件
        $('#tasks-table-body').on('click', '.enable-btn', function () {
            var $row = $(this).closest('tr');
            var taskName = $row.find('td').eq(1).text().trim(); // 任务名称在第2列（索引为1）
            var taskId = $(this).data('id');
            $('#enableTaskId').val(taskId);
            $('#enableTaskNameInput').val('');
            $('#confirmEnableBtn').prop('disabled', true);
            $('#enableValidationMessage').hide();
            $('#enableTaskNameInput').attr('placeholder', taskName);
            $('#enableTaskNameInput').attr('data-i18n-key-placeholder',  taskName);
            // 确保国际化应用到模态框
            i18n.applyTranslations();

            $('#enableConfirmModal').modal('show');
        });

        // 确认启用按钮点击事件
        $('#confirmEnableBtn').off('click').on('click', function() {
            var inputName = $('#enableTaskNameInput').val().trim();
            var taskId = $('#enableTaskId').val();
            var expectedName = $('#enableTaskNameInput').attr('placeholder');

            if (inputName === expectedName) {
                // 通过API启用任务
                makeApiCall('POST', `/tasks/${taskId}/enable`, null,
                    function () {
                        $('#enableConfirmModal').modal('hide');
                        showFeedback(i18n.translate('tasksPage.feedback.taskEnabled', "Task {{taskId}} enabled successfully!").replace('{{taskId}}', taskId), false);
                        loadTasks();
                    },
                    function (jqXHR) {
                        showFeedback(i18n.translate('tasksPage.feedback.errorEnabling', "Error enabling task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                    }
                );
            } else {
                $('#enableValidationMessage').text(i18n.translate('tasksPage.enableConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
            }
        });

        // 输入框内容变化时验证任务名称（启用确认）
        $('#enableTaskNameInput').off('input').on('input', function () {
            var inputName = $(this).val().trim();
            var expectedName = $(this).attr('placeholder');

            if (inputName === expectedName) {
                $('#confirmEnableBtn').prop('disabled', false);
                $('#enableValidationMessage').hide();
            } else {
                $('#confirmEnableBtn').prop('disabled', true);
                if (inputName && inputName.length > 0) {
                    $('#enableValidationMessage').text(i18n.translate('tasksPage.enableConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
                } else {
                    $('#enableValidationMessage').hide();
                }
            }
        });

        // 监听Enter键在输入框中的按下事件（启用确认）
        $('#enableTaskNameInput').keypress(function(e) {
            if (e.which === 13 && !$('#confirmEnableBtn').prop('disabled')) {
                $('#confirmEnableBtn').click();
            }
        });

        // 禁用按钮点击事件
        $('#tasks-table-body').on('click', '.disable-btn', function () {
            var $row = $(this).closest('tr');
            var taskName = $row.find('td').eq(1).text().trim(); // 任务名称在第2列（索引为1）
            var taskId = $(this).data('id');
            $('#disableTaskId').val(taskId);
            $('#disableTaskNameInput').val('');
            $('#confirmDisableBtn').prop('disabled', true);
            $('#disableValidationMessage').hide();
            $('#disableTaskNameInput').attr('placeholder', taskName);
            $('#disableTaskNameInput').attr('data-i18n-key-placeholder',  taskName);
            // 确保国际化应用到模态框
            i18n.applyTranslations();

            $('#disableConfirmModal').modal('show');
        });

        // 确认禁用按钮点击事件
        $('#confirmDisableBtn').off('click').on('click', function() {
            var inputName = $('#disableTaskNameInput').val().trim();
            var taskId = $('#disableTaskId').val();
            var expectedName = $('#disableTaskNameInput').attr('placeholder');

            if (inputName === expectedName) {
                // 通过API禁用任务
                makeApiCall('POST', `/tasks/${taskId}/disable`, null,
                    function () {
                        $('#disableConfirmModal').modal('hide');
                        showFeedback(i18n.translate('tasksPage.feedback.taskDisabled', "Task {{taskId}} disabled successfully!").replace('{{taskId}}', taskId), false);
                        loadTasks();
                    },
                    function (jqXHR) {
                        showFeedback(i18n.translate('tasksPage.feedback.errorDisabling', "Error disabling task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                    }
                );
            } else {
                $('#disableValidationMessage').text(i18n.translate('tasksPage.disableConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
            }
        });

        // 输入框内容变化时验证任务名称（禁用确认）
        $('#disableTaskNameInput').off('input').on('input', function () {
            var inputName = $(this).val().trim();
            var expectedName = $(this).attr('placeholder');

            if (inputName === expectedName) {
                $('#confirmDisableBtn').prop('disabled', false);
                $('#disableValidationMessage').hide();
            } else {
                $('#confirmDisableBtn').prop('disabled', true);
                if (inputName && inputName.length > 0) {
                    $('#disableValidationMessage').text(i18n.translate('tasksPage.disableConfirm.validationError', '任务名称不匹配，请输入正确的任务名称')).show();
                } else {
                    $('#disableValidationMessage').hide();
                }
            }
        });

        // 监听Enter键在输入框中的按下事件（禁用确认）
        $('#disableTaskNameInput').keypress(function(e) {
            if (e.which === 13 && !$('#confirmDisableBtn').prop('disabled')) {
                $('#confirmDisableBtn').click();
            }
        });

        // 查看下次执行时间按钮点击事件
        $('#tasks-table-body').on('click', '.next-runs-btn', function () {
            const taskId = $(this).data('id');
            const cronExpression = $(this).data('cron');
            const startDate = $(this).data('start-date');
            const endDate = $(this).data('end-date');
            const calendarGroup = $(this).data('calendar-group');

            if (!cronExpression || cronExpression.trim() === '') {
                showFeedback(i18n.translate('tasksPage.feedback.noCronExpression', 'This task does not have a CRON expression'), true);
                return;
            }

            // 创建模态框显示下次执行时间
            const modalHtml = `
                <div class="modal fade" id="nextRunsModal" tabindex="-1" role="dialog" aria-labelledby="nextRunsModalLabel" aria-hidden="true">
                    <div class="modal-dialog modal-lg" role="document">
                        <div class="modal-content">
                            <div class="modal-header bg-primary text-white">
                                <h5 class="modal-title" id="nextRunsModalLabel">
                                    <i class="bi bi-calendar-check"></i> ${i18n.translate('tasksPage.modal.nextRunsTitle', 'Next Execution Times')}
                                </h5>
                                <button type="button" class="close text-white" data-dismiss="modal" aria-label="Close">
                                    <span aria-hidden="true">&times;</span>
                                </button>
                            </div>
                            <div class="modal-body">
                                <div class="text-center">
                                    <div class="spinner-border spinner-border-sm text-primary" role="status">
                                        <span class="sr-only">${i18n.translate('common.loading', 'Loading...')}</span>
                                    </div>
                                    <p class="mt-2">${i18n.translate('tasksPage.modal.calculatingNextRuns', 'Calculating next execution times...')}</p>
                                </div>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-dismiss="modal">
                                    <i class="bi bi-x-circle"></i> ${i18n.translate('common.close', 'Close')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            // 移除之前的模态框（如果存在）
            $('#nextRunsModal').remove();

            // 添加模态框到页面
            $('body').append(modalHtml);

            // 显示模态框
            $('#nextRunsModal').modal('show');

            // 准备API请求参数
            const apiParams = {
                cronExpression: cronExpression,
                count: 5  // 请求5个下次执行时间
            };

            // 添加可选参数
            if (startDate) {
                apiParams.startDate = startDate;
            }
            if (endDate) {
                apiParams.endDate = endDate;
            }
            if (calendarGroup) {
                apiParams.calendarGroup = calendarGroup;
            }

            // 调用API获取下次执行时间 - 使用新的端点
            makeApiCall('GET', `/tasks/${taskId}/next-runs?count=15`, null,
                function (response) {
                    const modalBody = $('#nextRunsModal .modal-body');

                    if (response && response.isValid && response.nextExecutionTimes && response.nextExecutionTimes.length > 0) {
                        let contentHtml = `
                            <div class="alert alert-info mb-3">
                                <h6 class="alert-heading">
                                    <i class="bi bi-info-circle"></i> ${i18n.translate('tasksPage.modal.taskInfo', 'Task Information')}
                                </h6>
                                <hr class="my-2">
                                <p class="mb-1">
                                    <strong>${i18n.translate('tasksPage.modal.taskId', 'Task ID')}:</strong> ${taskId}
                                </p>
                                <p class="mb-0">
                                    <strong>${i18n.translate('tasksPage.modal.cronExpression', 'CRON Expression')}:</strong> 
                                    <code class="text-dark">${cronExpression}</code>
                                </p>
                            </div>
                            <h6 class="mb-3">
                                <i class="bi bi-calendar3"></i> ${i18n.translate('tasksPage.modal.scheduledExecutions', 'Scheduled Executions')}:
                            </h6>
                            <div class="list-group">
                        `;

                        // 显示所有返回的执行时间
                        response.nextExecutionTimes.forEach((executionTime, index) => {
                            const runTime = new Date(executionTime);
                            const formattedTime = runTime.toLocaleString();
                            const relativeTime = getRelativeTime(runTime);
                            const dayOfWeek = runTime.toLocaleDateString('default', { weekday: 'long' });

                            // 为第一个执行时间添加特殊样式
                            const itemClass = index === 0 ? 'list-group-item-primary' : '';
                            const badge = index === 0 ? 'badge-success' : 'badge-primary';

                            contentHtml += `
                                <div class="list-group-item ${itemClass}">
                                    <div class="d-flex w-100 justify-content-between align-items-center">
                                        <div>
                                            <h6 class="mb-1">
                                                <i class="bi bi-clock"></i> ${formattedTime}
                                            </h6>
                                            <small class="text-muted">${dayOfWeek}</small>
                                        </div>
                                        <div class="text-right">
                                            <span class="badge ${badge} badge-pill">${relativeTime}</span>
                                            ${index === 0 ? '<br><small class="text-muted">' + i18n.translate('tasksPage.modal.nextExecution', 'Next Execution') + '</small>' : ''}
                                        </div>
                                    </div>
                                </div>
                            `;
                        });

                        contentHtml += '</div>';

                        // 添加时区信息
                        const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
                        contentHtml += `
                            <div class="mt-3 text-muted small text-center">
                                <i class="bi bi-globe"></i> ${i18n.translate('tasksPage.modal.timezone', 'Timezone')}: ${timezone}
                            </div>
                        `;

                        modalBody.html(contentHtml);
                    } else if (response && !response.isValid) {
                        // CRON表达式无效
                        modalBody.html(`
                            <div class="alert alert-danger">
                                <h5 class="alert-heading">
                                    <i class="bi bi-x-circle"></i> ${i18n.translate('tasksPage.modal.invalidCron', 'Invalid CRON Expression')}
                                </h5>
                                <hr>
                                <p class="mb-0">${response.error || i18n.translate('tasksPage.modal.invalidCronDesc', 'The CRON expression is not valid.')}</p>
                            </div>
                        `);
                    } else {
                        // 其他错误情况
                        modalBody.html(`
                            <div class="alert alert-warning">
                                <i class="bi bi-exclamation-triangle"></i> ${i18n.translate('tasksPage.modal.noExecutionTimes', 'No noExecutionTimes')}
                            </div>
                        `);
                    }
                },
                function (jqXHR) {
                    const modalBody = $('#nextRunsModal .modal-body');
                    let errorMsg = i18n.translate('tasksPage.modal.errorCalculatingNextRuns', 'Error calculating next execution times');

                    if (jqXHR.responseJSON) {
                        if (jqXHR.responseJSON.error) {
                            errorMsg = jqXHR.responseJSON.error;
                        } else if (jqXHR.responseJSON.message) {
                            errorMsg = jqXHR.responseJSON.message;
                        }
                    }

                    modalBody.html(`
                        <div class="alert alert-danger">
                            <h5 class="alert-heading">
                                <i class="bi bi-x-circle"></i> ${i18n.translate('common.error', 'Error')}
                            </h5>
                            <hr>
                            <p class="mb-0">${errorMsg}</p>
                        </div>
                    `);
                }
            );

            // 清理模态框
            $('#nextRunsModal').on('hidden.bs.modal', function () {
                $(this).remove();
            });
        });

        // 辅助函数：计算相对时间
        function getRelativeTime(date) {
            const now = new Date();
            const diff = date - now;
            const minutes = Math.floor(diff / 60000);
            const hours = Math.floor(minutes / 60);
            const days = Math.floor(hours / 24);
            const weeks = Math.floor(days / 7);
            const months = Math.floor(days / 30);

            if (months > 0) {
                const monthText = months === 1
                    ? i18n.translate('tasksPage.modal.inMonth', 'in 1 month')
                    : i18n.translate('tasksPage.modal.inMonths', 'in {{months}} months').replace('{{months}}', months);
                return monthText;
            } else if (weeks > 0) {
                const weekText = weeks === 1
                    ? i18n.translate('tasksPage.modal.inWeek', 'in 1 week')
                    : i18n.translate('tasksPage.modal.inWeeks', 'in {{weeks}} weeks').replace('{{weeks}}', weeks);
                return weekText;
            } else if (days > 0) {
                const dayText = days === 1
                    ? i18n.translate('tasksPage.modal.tomorrow', 'tomorrow')
                    : i18n.translate('tasksPage.modal.inDays', 'in {{days}} days').replace('{{days}}', days);
                return dayText;
            } else if (hours > 0) {
                const hourText = hours === 1
                    ? i18n.translate('tasksPage.modal.inHour', 'in 1 hour')
                    : i18n.translate('tasksPage.modal.inHours', 'in {{hours}} hours').replace('{{hours}}', hours);
                return hourText;
            } else if (minutes > 0) {
                const minuteText = minutes === 1
                    ? i18n.translate('tasksPage.modal.inMinute', 'in 1 minute')
                    : i18n.translate('tasksPage.modal.inMinutes', 'in {{minutes}} minutes').replace('{{minutes}}', minutes);
                return minuteText;
            } else if (diff > 0) {
                return i18n.translate('tasksPage.modal.soon', 'soon');
            } else {
                // 如果时间已过
                const absDiff = Math.abs(diff);
                const pastMinutes = Math.floor(absDiff / 60000);
                const pastHours = Math.floor(pastMinutes / 60);
                const pastDays = Math.floor(pastHours / 24);

                if (pastDays > 0) {
                    return i18n.translate('tasksPage.modal.daysAgo', '{{days}} days ago').replace('{{days}}', pastDays);
                } else if (pastHours > 0) {
                    return i18n.translate('tasksPage.modal.hoursAgo', '{{hours}} hours ago').replace('{{hours}}', pastHours);
                } else if (pastMinutes > 0) {
                    return i18n.translate('tasksPage.modal.minutesAgo', '{{minutes}} minutes ago').replace('{{minutes}}', pastMinutes);
                } else {
                    return i18n.translate('tasksPage.modal.justNow', 'just now');
                }
            }
        }

        // 保存边缘按钮点击事件
        $('#saveEdgeBtn').on('click', function () {
            const fromNode = $('#edgeModalFromNode').val();
            const toNode = $('#edgeModalToNode').val();
            const expression = $('#edgeExpression').val();
            const priority = parseInt($('#edgePriority').val()) || 0;
            const isEditing = $('#edgeModalIsEditing').val() === 'true';
            const arrayIndex = parseInt($('#edgeModalArrayIndex').val());

            let currentNodesJson = $('#workflowNodesJson').val();
            let nodesArray = [];
            try {
                if (currentNodesJson && currentNodesJson.trim() !== '') {
                    nodesArray = JSON.parse(currentNodesJson);
                    if (!Array.isArray(nodesArray)) {
                        // 处理非数组情况
                    }
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeHelperErrorParseNodes', 'Error parsing Workflow Nodes JSON.') + ': ' + e.message, true);
                return;
            }

            let currentEdgesJson = $('#workflowEdgesJson').val();
            let potentialEdgesArray = [];
            try {
                if (currentEdgesJson && currentEdgesJson.trim() !== '') {
                    potentialEdgesArray = JSON.parse(currentEdgesJson);
                    if (!Array.isArray(potentialEdgesArray)) {
                        // 处理非数组情况
                    }
                }
            } catch (e) {
                potentialEdgesArray = [];
            }
            potentialEdgesArray = JSON.parse(JSON.stringify(potentialEdgesArray)); // 深拷贝

            const newEdge = {
                fromNodeId: fromNode,
                toNodeId: toNode,
                expression: expression || null,
                priority: priority
            };

            if (isEditing && arrayIndex > -1 && arrayIndex < potentialEdgesArray.length) {
                potentialEdgesArray[arrayIndex] = newEdge;
            } else {
                potentialEdgesArray.push(newEdge);
            }

            // 环检测
            if (nodesArray.length > 0 && potentialEdgesArray.length > 0) {
                if (detectCycleInWorkflow(nodesArray, potentialEdgesArray)) {
                    // 检测到环，显示警告信息，但仍然允许用户保存
                    const cycleWarning = i18n.translate('tasksPage.feedback.cycleDetected',
                        'Warning: A cycle was detected in the workflow graph. This may cause infinite loops during execution. Proceed with caution.');
                    showFeedback(cycleWarning, true);
                }
            }

            $('#workflowEdgesJson').val(JSON.stringify(potentialEdgesArray, null, 2));
            $('#edgePropertyModal').modal('hide');
            safeRedrawDAG();
        });

        // 删除边缘按钮点击事件
        $('#deleteEdgeBtn').on('click', function () {
            const arrayIndex = parseInt($('#edgeModalArrayIndex').val());
            let edgesJson = $('#workflowEdgesJson').val();
            let edgesArray = [];

            try {
                edgesArray = JSON.parse(edgesJson || '[]');
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.edgeErrorParse', "Error parsing existing Workflow Edges JSON: {{message}}. Starting with new array.").replace("{{message}}", e.message), true);
                return;
            }

            if (arrayIndex > -1 && arrayIndex < edgesArray.length) {
                edgesArray.splice(arrayIndex, 1);
                $('#workflowEdgesJson').val(JSON.stringify(edgesArray, null, 2));
            } else {
                showFeedback(i18n.translate('tasksPage.feedback.edgeDeleteError'), true);            }
            $('#edgePropertyModal').modal('hide');
            safeRedrawDAG();
        });

        // 边属性模态框显示事件
        $('#edgePropertyModal').on('show.bs.modal', function () {
            // 检查是新建还是编辑模式
            const isEditing = $('#edgeModalIsEditing').val() === 'true';

            // 设置帮助文本
            const expressionHelpText = i18n.translate('tasksPage.edgeModal.expressionHelp',
                '可以使用表达式决定边缘是否激活。例如：<br>' +
                '- <code>${nodeA_status} == \'SUCCESS\'</code> 当节点A成功时<br>' +
                '- <code>${nodeB_result} > 100</code> 当节点B结果大于100时<br>' +
                '留空则表示无条件激活。');

            const priorityHelpText = i18n.translate('tasksPage.edgeModal.priorityHelp',
                '当多条边缘激活时，优先级越高（数值越大）的边缘越先被执行。<br>' +
                '默认值为0，相同优先级则按添加顺序执行。');

            // 添加或更新帮助文本，添加Bootstrap提示框样式
            $('#expressionHelpText').remove();
            $('<small id="expressionHelpText" class="form-text text-muted"></small>')
                .html(expressionHelpText)
                .insertAfter('#edgeExpression');

            $('#priorityHelpText').remove();
            $('<small id="priorityHelpText" class="form-text text-muted"></small>')
                .html(priorityHelpText)
                .insertAfter('#edgePriority');

            // 为表达式字段添加自动完成功能
            const fromNodeId = $('#edgeModalFromNode').val();

            // 常见变量的自动完成提示
            const autoCompleteTerms = [
                `\${${fromNodeId}_status}`,
                `\${${fromNodeId}_result}`,
                `\${${fromNodeId}_execution_time}`,
                '\'SUCCESS\'',
                '\'FAILED\'',
                '\'COMPLETED\'',
                '==',
                '!=',
                '>',
                '<',
                '>=',
                '<='
            ];

            // 假设我们这里使用简单的自动填充功能，后续可以考虑集成更复杂的库
            $('#edgeExpression').attr('list', 'expressionSuggestions');

            // 移除之前的数据列表，避免重复添加
            $('#expressionSuggestions').remove();

            // 添加数据列表
            const datalist = $('<datalist id="expressionSuggestions"></datalist>');
            autoCompleteTerms.forEach(term => {
                datalist.append($('<option></option>').val(term));
            });

            $('#edgeExpression').after(datalist);
        });

        // 边属性模态框隐藏事件
        $('#edgePropertyModal').on('hidden.bs.modal', function () {
            if (window.selectedSourceElement) {
                window.selectedSourceElement.find('rect').attr('fill', '#fff');
            }
            window.selectedSourceNodeId = null;
            window.selectedSourceElement = null;
            $('#edgeExpression').val('');
            $('#edgePriority').val('0');
            $('#edgeModalIsEditing').val('false');
            $('#edgeModalArrayIndex').val('-1');
            $('#deleteEdgeBtn').hide();
        });

        // DAG容器点击事件
        $('#dagContainer').on('click', function (event) {
            const target = $(event.target);
            if (window.selectedSourceElement && (
                target.is('#dagContainer') || target.is('svg') ||
                target.is('svg > defs') || target.is('svg > marker') ||
                target.is('svg > path') || target.is('svg > rect') ||
                target.is('svg > text')
            )) {
                // 处理点击事件
            }
        });

        // 保存节点更改按钮点击事件
        $('#saveNodeChangesBtn').on('click', function () {
            const updatedNodeName = $('#editNodeName').val().trim();
            const updatedNodeParamsString = $('#editNodeParams').val();
            const nodeIndex = parseInt($('#editingNodeArrayIndex').val());

            let updatedParams;
            try {
                updatedParams = updatedNodeParamsString.trim() === '' ? {} : JSON.parse(updatedNodeParamsString);
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeParamsInvalid', 'JSON参数格式无效:') + ' ' + e.message, true);
                return;
            }

            // 处理新节点创建或现有节点编辑
            if (nodeIndex === -1) {
                // 创建新节点
                const nodeId = $('#editNodeId').val().trim();
                const taskConfigId = $('#editTaskConfigId').val();

                if (!nodeId) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeIdRequired', '节点ID是必需的'), true);
                    return;
                }

                let currentNodesJson = $('#workflowNodesJson').val();
                let nodesArray = [];
                try {
                    nodesArray = JSON.parse(currentNodesJson || '[]');
                } catch (e) {
                    nodesArray = [];
                }

                // 检查节点ID是否重复
                if (nodesArray.some(n => n.nodeId === nodeId)) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeIdDuplicate', '节点ID已存在: ') + nodeId, true);
                    return;
                }

                // 创建新节点对象
                const newNode = {
                    nodeId: nodeId,
                    nodeName: updatedNodeName || nodeId,
                    parameters: updatedParams
                };

                if (taskConfigId && taskConfigId !== '') {
                    newNode.taskConfigId = parseInt(taskConfigId);
                }

                // 如果处于图形模式，添加位置信息 - 默认放在中心位置
                if (window.graphModeEnabled) {
                    const container = $('#dagContainer');
                    const containerWidth = container.width();
                    const containerHeight = container.height();

                    newNode.position = {
                        x: (containerWidth / 2) - 75, // 节点宽度的一半
                        y: (containerHeight / 2) - 30  // 节点高度的一半
                    };
                }

                // 添加到节点数组
                nodesArray.push(newNode);
                $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));

                showFeedback(i18n.translate('tasksPage.feedback.nodeAdded', '节点已添加: ') + nodeId, false);
            } else {
                // 编辑现有节点
                let currentNodesJson = $('#workflowNodesJson').val();
                let nodesArray = [];
                try {
                    nodesArray = JSON.parse(currentNodesJson || '[]');
                    if (!Array.isArray(nodesArray)) {
                        // 处理非数组情况
                    }
                } catch (e) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析工作流节点时出错: ') + e.message, true);
                    return;
                }

                if (nodeIndex >= 0 && nodeIndex < nodesArray.length) {
                    // 获取选择的任务配置ID
                    const taskConfigId = $('#editTaskConfigId').val();

                    // 更新节点信息
                    nodesArray[nodeIndex].nodeName = updatedNodeName;
                    nodesArray[nodeIndex].parameters = updatedParams;

                    // 更新任务配置ID（如果选择了）
                    if (taskConfigId) {
                        nodesArray[nodeIndex].taskConfigId = parseInt(taskConfigId);
                    }

                    $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
                    showFeedback(i18n.translate('tasksPage.feedback.nodeUpdated', '节点已更新: ') + nodesArray[nodeIndex].nodeId, false);
                } else {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeNotFound', '找不到要编辑的节点'), true);
                    return;
                }
            }            // 临时禁用背景点击关闭功能，确保关闭时不会影响其他模态窗口
            try {
                var modalInstance = $('#workflowNodeEditModal').data('bs.modal');
                if (modalInstance && modalInstance._config) {
                    var originalBackdrop = modalInstance._config.backdrop;
                    modalInstance._config.backdrop = 'static';
                    $('#workflowNodeEditModal').modal('hide');
                    // 恢复原始设置
                    setTimeout(function() {
                        modalInstance._config.backdrop = originalBackdrop;
                    }, 200);
                } else {
                    $('#workflowNodeEditModal').modal('hide');
                }            } catch (e) {
                console.error('关闭节点编辑窗口时出错:', e);
                // 如果出错，使用默认方式关闭
                $('#workflowNodeEditModal').modal('hide');            }
            // 安全调用redrawDAG函数
            safeRedrawDAG();

            // 确保任务表单模态窗口可见
            setTimeout(function() {
                if (!$('#taskFormModal').hasClass('show')) {
                    $('#taskFormModal').modal('show');
                }
            }, 300);
        });
        // 工作流节点编辑模态框隐藏事件
        $('#workflowNodeEditModal').on('hidden.bs.modal', function () {
            $('#editNodeName').val('');
            $('#editNodeParams').val('{}');
            $('#displayNodeId').text('');
            $('#editTaskConfigId').empty(); // 清空任务选择下拉框
            $('#editingNodeArrayIndex').val('');
            // 确保任务表单模态窗口保持打开状态
            setTimeout(function() {
                if (!$('#taskFormModal').hasClass('show')) {
                    $('#taskFormModal').modal('show');
                }
            }, 100); // 小延迟，确保在所有关闭操作完成后执行

            // 恢复节点样式，避免 selectedSourceElement 为 null 时引起的错误
            if (window.selectedSourceElement) {
                window.selectedSourceElement.find('rect')
                    .attr('fill', '#fff')
                    .attr('stroke', '#007bff')
                    .attr('stroke-width', 2)
                    .css('filter', 'none');
            }

            // 清理临时元素和状态
            $('#tempAnimatedEdge').remove();
            $('#tempEdgeAnimation').remove();
            $('#dagContainer').find('.node-action-status').remove();

            // 重置选择状态
            window.selectedSourceNodeId = null;
            window.selectedSourceElement = null;

            // 确保全局安全检查，防止出现其他不一致的状态
            if (typeof window.checkWorkflowSafetyState === 'function') {
                window.checkWorkflowSafetyState();
            }
        });        // 添加工作流DAG上下文菜单功能
        $('#dagContainer').on('contextmenu', function(e) {
            // 移除之前的任何上下文菜单
            $('.dag-context-menu').remove();

            // 防止默认的右键菜单
            e.preventDefault();
            e.stopImmediatePropagation(); // 阻止其他处理器干扰

            console.log('显示DAG上下文菜单', e.pageX, e.pageY);

            // 获取鼠标位置
            const mouseX = e.pageX;
            const mouseY = e.pageY;

            // 创建上下文菜单
            const contextMenu = $('<div>')
                .addClass('dag-context-menu')
                .css({
                    position: 'absolute',
                    top: mouseY + 'px',
                    left: mouseX + 'px',
                    background: 'white',
                    border: '1px solid #ccc',
                    borderRadius: '3px',
                    boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
                    padding: '5px 0',
                    zIndex: 1000
                });
            // 基础菜单项
            const baseMenuItems = [
                {
                    text: i18n.translate('tasksPage.dagContextMenu.refreshView', '刷新视图'),
                    icon: 'bi-arrow-repeat',

                    action: function() {
                        safeRedrawDAG();
                    }
                },
                {
                    text: i18n.translate('tasksPage.dagContextMenu.toggleGuide', '显示/隐藏操作指南'),
                    icon: 'bi-info-circle',
                    action: function() {
                        const guide = $('#dagContainer').find('.dag-operation-guide');
                        if (guide.length) {
                            guide.toggle();
                        } else {
                            $('#dagContainer').append(window.showDagOperationGuide());
                        }
                    }
                },
                {
                    text: i18n.translate('tasksPage.dagContextMenu.formatJson', '格式化JSON数据'),
                    icon: 'bi-braces',
                    action: function() {
                        try {
                            // 美化节点JSON
                            const nodesJson = $('#workflowNodesJson').val();
                            if (nodesJson.trim() !== '') {
                                const parsedNodes = JSON.parse(nodesJson);
                                $('#workflowNodesJson').val(JSON.stringify(parsedNodes, null, 2));
                            }

                            // 美化边缘JSON
                            const edgesJson = $('#workflowEdgesJson').val();
                            if (edgesJson.trim() !== '') {
                                const parsedEdges = JSON.parse(edgesJson);
                                $('#workflowEdgesJson').val(JSON.stringify(parsedEdges, null, 2));
                            }

                            showFeedback(i18n.translate('tasksPage.feedback.jsonFormatted', 'JSON已成功格式化'), false);
                        } catch (e) {
                            showFeedback(i18n.translate('tasksPage.feedback.jsonFormatError', 'JSON格式化失败: ') + e.message, true);
                        }
                    }
                }
            ];

            // 图形模式特有菜单项
            const graphModeItems = [];
            if (window.graphModeEnabled) {
                graphModeItems.push({
                    text: i18n.translate('tasksPage.dagContextMenu.addNode', '添加新节点'),
                    icon: 'bi-plus-circle',
                    action: function() {
                        // 调用添加节点对话框
                        if (typeof window.openAddNodeDialog === 'function') {
                            console.log('找到openAddNodeDialog函数，调用中...');
                            window.openAddNodeDialog();
                        } else {
                            // 如果函数不存在，尝试即时定义它
                            window.openAddNodeDialog = function() {
                                try {
                                    console.log('使用动态创建的openAddNodeDialog');

                                    // 设置模态窗口标题
                                    $('#workflowNodeEditModalLabel').text('添加工作流节点');

                                    // 设置为新节点模式
                                    $('#editingNodeArrayIndex').val('-1'); // -1 表示新建节点

                                    // 显示节点ID输入框（仅在添加新节点时需要）
                                    $('.node-id-input-group').show();

                                    // 生成一个默认节点ID
                                    const timestamp = new Date().getTime();
                                    const defaultNodeId = 'node_' + timestamp.toString().substring(timestamp.toString().length - 6);
                                    $('#editNodeId').val(defaultNodeId);

                                    // 清空名称和参数
                                    $('#editNodeName').val('');
                                    $('#editNodeParams').val('{}');

                                    // 设置显示节点ID
                                    $('#displayNodeId').text('新节点');

                                    // 隐藏删除节点按钮
                                    $('#deleteNodeBtn').hide();

                                    // 打开模态窗口
                                    $('#workflowNodeEditModal').modal('show');
                                } catch (e) {
                                    console.error('打开添加节点对话框时出错:', e);
                                    showFeedback('打开添加节点对话框时出错: ' + e.message, true);
                                }
                            };

                            // 立即调用
                            window.openAddNodeDialog();
                        }
                    }
                });

                graphModeItems.push({
                    text: i18n.translate('tasksPage.dagContextMenu.arrangeNodes', '自动排列节点'),
                    icon: 'bi-grid',
                    action: function() {
                        // 如果有节点自动布局函数，调用它
                        if (typeof window.arrangeNodesInGrid === 'function') {
                            window.arrangeNodesInGrid();
                        } else {
                            showFeedback('自动排列功能尚未实现', true);
                        }
                    }
                });
            }

            // 合并菜单项
            const menuItems = [...graphModeItems, ...baseMenuItems];


            // 为每个菜单项创建DOM元素
            menuItems.forEach(function(item) {
                const menuItem = $('<div>')
                    .addClass('dag-context-menu-item')
                    .css({
                        padding: '6px 15px',
                        cursor: 'pointer',
                        whiteSpace: 'nowrap'
                    })
                    .hover(
                        function() { $(this).css('background-color', '#f0f0f0'); },
                        function() { $(this).css('background-color', ''); }
                    )
                    .on('click', function() {
                        $('.dag-context-menu').remove();
                        item.action();
                    });

                // 添加图标和文本
                if (item.icon) {
                    menuItem.append($('<i>').addClass(item.icon).css('margin-right', '8px'));
                }
                menuItem.append(document.createTextNode(item.text));

                contextMenu.append(menuItem);
            });

            // 将菜单添加到DOM
            $('body').append(contextMenu);
            // 点击其他地方关闭菜单
            $(document).on('click', function closeMenu(e) {
                if (!$(e.target).closest('.dag-context-menu').length) {
                    $('.dag-context-menu').remove();
                    $(document).off('click', closeMenu);
                }
            });

            // 确保菜单可见并置于前方
            contextMenu.css('z-index', 9999);
        });

        // 添加任务按钮点击事件
        $('#addTaskBtn').click(function () {
            $('#taskType').trigger('change');
        });

        // 验证CRON表达式按钮点击事件
        $('#validateCronBtn').on('click', function () {
            const cronExpression = $('#cronExpression').val();
            if (!cronExpression || cronExpression.trim() === '') {
                const errorMsg = i18n.translate('tasksPage.modal.missingCronError', 'Please provide a valid CRON expression');
                $('#cronValidationResult').html(`<div class="alert alert-danger">${errorMsg}</div>`).show();
                return;
            }

            // 显示加载指示
            $('#cronValidationResult').html(`<div class="text-center"><div class="spinner-border spinner-border-sm text-primary" role="status"></div> ${i18n.translate('common.loading', 'Loading...')}</div>`).show();

            // 调用API验证CRON表达式 - 使用正确的响应字段
            makeApiCall('GET', '/tasks/validate-cron', {
                    cronExpression: cronExpression,
                    count: 5
                },
                function (response) {
                    if (response && response.isValid) {
                        let resultHtml = `<div class="alert alert-success">
                            <i class="bi bi-check-circle"></i> ${i18n.translate('tasksPage.modal.validCronExpression', 'Valid CRON expression')}
                        </div>`;

                        if (response.nextExecutionTimes && response.nextExecutionTimes.length > 0) {
                            const nextRunsLabel = i18n.translate('tasksPage.modal.nextRuns', 'Next runs:');
                            resultHtml += `<div class="mt-2"><strong>${nextRunsLabel}</strong><ul class="list-unstyled mt-2">`;

                            response.nextExecutionTimes.forEach((executionTime, index) => {
                                const runTime = new Date(executionTime);
                                const formattedTime = runTime.toLocaleString();
                                const relativeTime = getRelativeTime(runTime);
                                resultHtml += `<li><i class="bi bi-clock text-muted"></i> ${formattedTime} <span class="text-muted">(${relativeTime})</span></li>`;
                            });

                            resultHtml += '</ul></div>';
                        }

                        $('#cronValidationResult').html(resultHtml).show();
                    } else {
                        const invalidCronMsg = i18n.translate('tasksPage.modal.invalidCronExpression', 'Invalid CRON expression');
                        const errorDetail = response && response.error ? response.error : '';
                        $('#cronValidationResult').html(`
                            <div class="alert alert-danger">
                                <i class="bi bi-x-circle"></i> ${invalidCronMsg}
                                ${errorDetail ? '<br><small class="text-muted">' + errorDetail + '</small>' : ''}
                            </div>
                        `).show();
                    }
                },
                function (jqXHR) {
                    let errorMsg = i18n.translate('tasksPage.modal.errorValidatingCron', 'Error validating CRON expression');
                    if (jqXHR.responseJSON) {
                        if (jqXHR.responseJSON.error) {
                            errorMsg = jqXHR.responseJSON.error;
                        } else if (jqXHR.responseJSON.message) {
                            errorMsg = jqXHR.responseJSON.message;
                        }
                    }
                    $('#cronValidationResult').html(`
                        <div class="alert alert-danger">
                            <i class="bi bi-x-circle"></i> ${errorMsg}
                       
                        </div>
                    `).show();
                }
            );
        });

        // 应用过滤器按钮点击事件
        $('#applyTaskFiltersBtn').click(function() {
            loadTasks();
        });

        // 重置过滤器按钮点击事件
        $('#resetTaskFiltersBtn').click(function() {
            $('#filterTaskName').val('');
            $('#filterTaskGroup').val('');
            $('#filterTaskType').val('');
            $('#filterIsActive').val('');
            loadTasks();
        });

        // 工作流添加/更新节点按钮实现
        $('#addNodeToJsonBtn').on('click', function() {
            const taskId = $('#availableTasksForNodes').val();
            const nodeId = $('#wfNodeId').val().trim();
            const nodeName = $('#wfNodeName').val().trim();
            const nodeParams = $('#wfNodeParams').val().trim();

            if (!nodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeIdRequired', 'Node ID is required.'), true);
                return;
            }

            // 尝试解析节点参数
            let parsedParams = {};
            if (nodeParams) {
                try {
                    parsedParams = JSON.parse(nodeParams);
                } catch (e) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeParamsInvalid', 'Invalid node parameters JSON.'), true);
                    return;
                }
            }

            // 准备新节点
            const newNode = {
                nodeId: nodeId,
                nodeName: nodeName || nodeId,
                parameters: parsedParams
            };

            if (taskId) {
                newNode.taskConfigId = parseInt(taskId);
            }

            // 读取现有节点
            let existingNodes = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                existingNodes = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeHelperErrorParseNodes', 'Error parsing workflow nodes JSON.'), true);
                existingNodes = [];
            }

            // 检查是否有同ID节点
            const existingNodeIndex = existingNodes.findIndex(node => node.nodeId === nodeId);
            if (existingNodeIndex >= 0) {
                // 更新现有节点
                existingNodes[existingNodeIndex] = { ...existingNodes[existingNodeIndex], ...newNode };
                showFeedback(i18n.translate('tasksPage.feedback.nodeUpdated', 'Node {{nodeId}} updated.').replace('{{nodeId}}', nodeId), false);
            } else {
                // 添加新节点
                existingNodes.push(newNode);
                showFeedback(i18n.translate('tasksPage.feedback.nodeAdded', 'Node {{nodeId}} added.').replace('{{nodeId}}', nodeId), false);
            }

            // 更新节点JSON
            $('#workflowNodesJson').val(JSON.stringify(existingNodes, null, 2));

            // 清除表单
            $('#wfNodeId').val('');            $('#wfNodeName').val('');
            $('#wfNodeParams').val('{}');
            $('#availableTasksForNodes').val('');

            // 刷新DAG视图
            safeRedrawDAG();
        });

        // 快速添加节点
        $('.quick-add-node').on('click', function(e) {
            e.preventDefault();
            const nodeType = $(this).data('node-type');

            // 生成唯一ID
            const timestamp = new Date().getTime();
            const randomPart = Math.floor(Math.random() * 1000);

            let nodeId, nodeName, parameters = {};

            switch(nodeType) {
                case 'start':
                    nodeId = 'start_' + timestamp % 10000;
                    nodeName = '开始节点';
                    parameters = {
                        isStartNode: true,
                        description: '工作流起始点'
                    };
                    break;
                case 'end':
                    nodeId = 'end_' + timestamp % 10000;
                    nodeName = '结束节点';
                    parameters = {
                        isEndNode: true,
                        description: '工作流终止点'
                    };
                    break;
                case 'process':
                    nodeId = 'process_' + timestamp % 10000;
                    nodeName = '处理节点';
                    parameters = {
                        processType: 'standard',
                        description: '执行标准处理'
                    };
                    break;
                case 'decision':
                    nodeId = 'decision_' + timestamp % 10000;
                    nodeName = '决策节点';
                    parameters = {
                        decisionType: 'condition',
                        description: '基于条件分支执行流'
                    };
                    break;
                case 'parallel':
                    nodeId = 'parallel_' + timestamp % 10000;
                    nodeName = '并行节点';
                    parameters = {
                        parallelType: 'fork',
                        description: '创建并行执行分支'
                    };
                    break;
                default:
                    nodeId = 'node_' + timestamp % 10000;
                    nodeName = '普通节点';
                    parameters = {};
            }

            // 读取现有节点
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                nodesArray = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
            } catch(e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
                return;
            }

            // 创建新节点对象
            const newNode = {
                nodeId: nodeId,
                nodeName: nodeName,
                parameters: parameters
            };

            // 如果处于图形模式，添加位置信息并优化节点放置
            if (window.graphModeEnabled) {
                const container = $('#dagContainer');
                const svg = container.find('svg');

                // 获取可见区域的中心点
                const containerWidth = container.width();
                const containerHeight = container.height();
                const scrollLeft = container.scrollLeft();
                const scrollTop = container.scrollTop();

                // 计算良好的节点布局位置
                const existingNodes = nodesArray.length;
                let posX, posY;

                if (existingNodes === 0) {
                    // 第一个节点放在中央
                    posX = (containerWidth / 2) - 75; // 节点宽度的一半
                    posY = 50; // 顶部留出一些空间
                } else if (nodeType === 'start') {
                    // 开始节点放在顶部中央
                    posX = (containerWidth / 2) - 75;
                    posY = 30;
                } else if (nodeType === 'end') {
                    // 结束节点放在现有节点下方
                    posX = (containerWidth / 2) - 75;
                    posY = Math.max(...nodesArray.map(n => (n.position?.y || 0) + 100));
                } else if (nodeType === 'decision' || nodeType === 'parallel') {
                    // 决策和并行节点倾向于放在中间
                    posX = (containerWidth / 2) - 75 + Math.floor(Math.random() * 50);
                    posY = Math.max(...nodesArray.map(n => (n.position?.y || 0) + 100)) + 40;
                } else {
                    // 对于其他节点，根据类型添加到适当位置
                    const existingPositions = nodesArray.map(n => n.position || {x: 0, y: 0});

                    // 找到右下角的空白区域
                    const maxY = Math.max(100, ...existingPositions.map(p => p.y || 0));
                    const maxX = Math.max(100, ...existingPositions.map(p => p.x || 0));

                    // 添加一些随机偏移，避免节点完全重叠
                    const randomOffsetX = Math.floor(Math.random() * 50);
                    const randomOffsetY = Math.floor(Math.random() * 50);

                    if (nodeType === 'process') {
                        // 处理节点放在右侧
                        posX = maxX + 50 + randomOffsetX;
                        posY = 100 + randomOffsetY;
                    } else {
                        // 其他节点放在右侧
                        posX = maxX + 50 + randomOffsetX;
                        posY = 100 + randomOffsetY;
                    }
                }

                // 保证节点在可见区域内
                posX = Math.max(10, Math.min(posX, containerWidth - 160));
                posY = Math.max(10, posY);

                // 设置节点位置
                newNode.position = {
                    x: posX,
                    y: posY
                };
            }

            // 添加到数组
            nodesArray.push(newNode);

            // 更新JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));

            // 重绘DAG
            safeRedrawDAG();

            showFeedback(i18n.translate('tasksPage.feedback.quickNodeAdded', '已添加 ') + nodeName, false);
        });

        // 添加节点模态框显示函数
        window.showAddNodeModal = function() {
            // 确保节点助手区域和内容都可见
            const nodeHelperCard = $('#nodeHelperCard');
            const nodeHelperContent = $('#nodeHelperContent');

            nodeHelperCard.show();
            nodeHelperContent.show();
            $('#toggleNodeHelper i').removeClass('bi-chevron-down').addClass('bi-chevron-up');
            localStorage.setItem('nodeHelperHidden', 'false');

            // 滚动到节点助手区域，使其可见
            if (nodeHelperCard.length) {
                $('html, body').animate({
                    scrollTop: nodeHelperCard.offset().top - 100
                }, 300);

                // 高亮节点助手区域，提示用户
                nodeHelperCard.addClass('border-primary').css('box-shadow', '0 0 10px rgba(0,123,255,0.5)');
                setTimeout(function() {
                    nodeHelperCard.removeClass('border-primary').css('box-shadow', '');
                }, 2000);
            }

            // 显示添加节点的表单
            $('#availableTasksForNodes').focus();

            // 确保任务列表已加载
            loadAvailableTasksForNodes();
        };

        // 通用处理程序，用于所有"Format JSON"按钮
        $('body').on('click', '.format-json-btn', function () {
            const textareaId = $(this).data('target-textarea');
            const $textarea = $('#' + textareaId);
            if ($textarea.length) {
                const currentJson = $textarea.val();
                if (currentJson.trim() === '') {
                    // 可选地，显示没有内容可格式化的反馈，或者什么都不做
                    return;
                }
                try {
                    const parsedJson = JSON.parse(currentJson);
                    const formattedJson = JSON.stringify(parsedJson, null, 2); // 美化打印，缩进2个空格
                    $textarea.val(formattedJson);
                    showFeedback(i18n.translate('tasksPage.feedback.jsonFormatted', 'JSON formatted successfully.'), false);
                } catch (e) {
                    const errorMsg = i18n.translate('tasksPage.feedback.invalidJsonFormat', 'Invalid JSON: Could not format. Please check syntax. {{error}}')
                        .replace('{{error}}', e.message);
                    showFeedback(errorMsg, true);
                }
            } else {
                console.warn('Format JSON button clicked, but target textarea not found:', textareaId);
            }
        });

        // Cron 表达式建议功能
        $(document).ready(function() {
            const cronSuggestions = [
                { expression: '0 0/5 * * * ?', description: '每5分钟执行一次' },
                { expression: '0 0/10 * * * ?', description: '每10分钟执行一次' },
                { expression: '0 0/30 * * * ?', description: '每30分钟执行一次' },
                { expression: '0 0 * * * ?', description: '每小时执行一次' },
                { expression: '0 0 0 * * ?', description: '每天凌晨0点执行' },
                { expression: '0 0 12 * * ?', description: '每天中午12点执行' },
                { expression: '0 0 0 1 * ?', description: '每月1日凌晨0点执行' },
                { expression: '0 0 0 ? * MON', description: '每周一凌晨0点执行' },
                { expression: '0 0 0 1 1 ?', description: '每年1月1日凌晨0点执行' }
            ];

            const $cronInput = $('#cronExpression');
            const $suggestionsContainer = $('.cron-suggestions');

            // 填充建议列表
            cronSuggestions.forEach(suggestion => {
                const $item = $(`
                    <div class="cron-suggestion-item" data-expression="${suggestion.expression}">
                        ${suggestion.expression}
                        <div class="cron-desc">${suggestion.description}</div>
                    </div>
                `);
                $suggestionsContainer.append($item);
            });

            // 显示建议列表
            $cronInput.on('focus', function() {
                $suggestionsContainer.show();
            });

            // 点击建议项
            $suggestionsContainer.on('click', '.cron-suggestion-item', function() {
                const expression = $(this).data('expression');
                $cronInput.val(expression);
                $suggestionsContainer.hide();
            });

            // 点击外部隐藏
            $(document).on('click', function(event) {
                if (!$cronInput.is(event.target) && $cronInput.has(event.target).length === 0 &&
                    !$suggestionsContainer.is(event.target) && $suggestionsContainer.has(event.target).length === 0) {
                    $suggestionsContainer.hide();
                }
            });
        });
    });
}

/**
 * 安全调用initGraphMode函数的辅助方法
 * 避免直接引用window.initGraphMode可能导致的未定义错误
 * 增强版本：更完整的备用实现和更好的错误处理
 */
function safeInitGraphMode() {
    console.log('安全初始化图形模式');

    // 防止同时有多个初始化请求
    if (window.isInitializingGraphMode) {
        console.warn('图形模式初始化已在进行中，忽略重复调用');
        return;
    }

    try {
        window.isInitializingGraphMode = true;

        // 清理可能存在的旧状态
        $('#dagContainer').off('mousemove.graphMode');
        $('#dagContainer').off('mouseup.graphMode');

        // 首先尝试使用原始函数
        if (typeof window.initGraphMode === 'function') {
            // 使用原始函数
            try {
                window.initGraphMode();
                console.log('成功使用原始initGraphMode函数');
            } catch (e) {
                console.error('原始initGraphMode函数执行失败，切换到备用实现:', e);
                applyFallbackInitGraphMode();
            }
        } else {

            console.warn('initGraphMode函数未定义，使用备用实现');
            applyFallbackInitGraphMode();
        }
    } catch (e) {
        console.error('初始化图形模式时出错:', e);

        // 最后的备用实现 - 至少显示基本UI
        $('#graphModeControls').show();
        $('#workflowNodesJson, #workflowEdgesJson, #workflowJsonRefreshRow').hide();
        window.graphModeEnabled = true;
    } finally {
        // 确保状态标志被重置
        setTimeout(function() {
            window.isInitializingGraphMode = false;
        }, 500);
    }
}

// 备用的图形模式初始化实现
function applyFallbackInitGraphMode() {
    // 设置基本状态
    window.graphModeEnabled = true;
    window.nodePositionChanged = false;

    // 显示图形模式控件
    $('#graphModeControls').show();

    // 隐藏JSON编辑区
    $('#workflowNodesJson, #workflowEdgesJson, #workflowJsonRefreshRow').hide();

    // 重绘DAG以启用拖拽功能
    safeRedrawDAG(true);

    // 确保节点助手卡片可见
    if (typeof window.ensureNodeHelperVisible === 'function') {
        window.ensureNodeHelperVisible();
    } else {
        // 如果函数不可用，直接显示相关元素
        $('#workflowTaskFields').show();
        $('#nodeHelperCard').show();
        if (localStorage.getItem('nodeHelperHidden') !== 'true') {
            $('#nodeHelperContent').show();
        }
    }

    console.log("已应用备用的图形模式初始化");

    // 为拖拽功能添加最小实现
    if (typeof handleMouseMove !== 'function') {
        window.handleMouseMove = function(event) {
            // 简单的鼠标移动处理
            if (window.nodeDragging && window.currentDragNode) {
                console.log('节点拖动中...');
            }
        };
    }

    if (typeof handleMouseUp !== 'function') {
        window.handleMouseUp = function(event) {
            // 简单的鼠标释放处理
            window.nodeDragging = false;
            window.currentDragNode = null;
        };
    }
}

// 将安全版本暴露为全局函数
window.safeInitGraphMode = safeInitGraphMode;

/**
 * 提供默认的任务类型选项，作为API调用失败时的后备方案
 * @param {jQuery|string} selectElement - jQuery选择器对象或选择器字符串
 * @param {boolean} hideNotice - 是否隐藏通知信息
 */
function fillDefaultTaskOptions(selectElement, hideNotice) {
    console.log('使用默认任务选项填充下拉框');

    // 处理输入参数
    if (typeof selectElement === 'string') {
        selectElement = $(selectElement);
    } else if (!selectElement || !selectElement.length) {
        selectElement = $('#editTaskConfigId');
    }

    // 添加一些常见任务类型作为备用
    const defaultOptions = [
        { id: 'shell_1', name: 'Shell脚本任务', type: '脚本' },
        { id: 'http_1', name: 'HTTP请求任务', type: 'HTTP' },
        { id: 'db_1', name: 'SQL数据库任务', type: '数据库' },
        { id: 'file_1', name: '文件处理任务', type: '文件' },
        { id: 'api_1', name: 'REST API任务', type: 'API' },
        { id: 'mail_1', name: '邮件发送任务', type: '通知' }
    ];

    // 添加这些选项到下拉框
    defaultOptions.forEach(task => {
        selectElement.append(
            $('<option></option>')
                .attr('value', task.id)
                .text(`${task.name} (${task.type} - 默认选项)`)
        );
    });

    // 除非指定隐藏，否则添加提示用户这些是备用选项
    if (!hideNotice) {
        selectElement.append(
            $('<option></option>')
                .attr('value', '')
                .attr('disabled', 'disabled')
                .text('* 这些是备用选项，API加载失败时显示 *')
        );
    }
}

/**
 * 加载任务配置选项，优先使用缓存
 * 包含增强的错误处理和备用选项机制
 * @param {string} selectElementId - 选择框元素的ID，默认为'editTaskConfigId'
 */
function loadTaskConfigOptions(selectElementId) {
    try {
        // 确定选择框元素ID
        selectElementId = selectElementId || 'editTaskConfigId';
        console.log(`开始加载任务配置选项到 ${selectElementId}...`);

        // 清空现有选项
        const selectElement = $('#' + selectElementId);
        if (!selectElement.length) {
            console.error(`找不到选择器元素 #${selectElementId}`);
            return;
        }

        selectElement.empty();

        // 添加空选项
        selectElement.append(
            $('<option></option>')
                .attr('value', '')
                .text(i18n.translate('tasksPage.nodeModal.selectTaskConfig', '- 选择任务配置 -'))
        );

        // 添加加载中选项
        const loadingOption = $('<option></option>')
            .attr('value', '')
            .attr('disabled', 'disabled')
            .text('加载中...');
        selectElement.append(loadingOption);

        // 定义成功加载和失败加载后的处理函数
        const onTasksLoaded = function(tasks) {
            // 移除加载中选项
            loadingOption.remove();

            if (!tasks || !Array.isArray(tasks) || tasks.length === 0) {
                console.warn('没有可用的任务配置或返回格式不正确');
                fillDefaultTaskOptions(selectElement);
                return;
            }

            console.log(`成功加载任务配置，共 ${tasks.length} 个任务`);

            // 添加每个任务配置选项
            let nonWorkflowTasksCount = 0;
            tasks.forEach(function(task) {
                // 排除工作流类型任务
                if (task.taskType !== 10) { // 10 是工作流类型
                    const taskId = task.id || task.taskId;
                    const taskName = task.name || task.taskName;

                    if (taskId && taskName) {
                        selectElement.append(
                            $('<option></option>')
                                .attr('value', taskId)
                                .text(`${taskName} (ID: ${taskId})`)
                        );
                        nonWorkflowTasksCount++;
                    }
                }
            });

            // 如果没有非工作流任务，添加一些默认选项
            if (nonWorkflowTasksCount === 0) {
                console.log('未找到非工作流任务，使用默认选项');
                fillDefaultTaskOptions(selectElement);
            }
        };

        const onLoadError = function(jqXHR, textStatus, errorThrown) {
            const errorMsg = window.handleApiError ?
                window.handleApiError(jqXHR, textStatus, errorThrown, '加载任务配置列表') :
                `${jqXHR.status}: ${errorThrown || textStatus}`;

            console.error('无法从API加载任务配置: ' + errorMsg);
            loadingOption.remove(); // 移除加载中选项

            // 添加错误状态选项
            selectElement.append(
                $('<option></option>')
                    .attr('value', '')
                    .attr('disabled', 'disabled')
                    .text('加载失败: ' + errorMsg)
            );

            // 使用默认任务选项作为备用
            fillDefaultTaskOptions(selectElement);
        };

        // 直接从缓存获取任务配置，避免额外的API调用
        if (window.cachedTasks && Array.isArray(window.cachedTasks) && window.cachedTasks.length > 0) {
            console.log('从缓存加载任务配置，共 ' + window.cachedTasks.length + ' 个任务');
            onTasksLoaded(window.cachedTasks);
            return;
        }

        // 如果没有缓存，从服务器获取任务配置列表
        console.log('从服务器加载任务配置...');
        makeApiCall('GET', '/tasks', null,
            function(response) {
                // 缓存任务列表，以便将来使用
                if (Array.isArray(response)) {
                    window.cachedTasks = response;
                }
                onTasksLoaded(response);
            },
            onLoadError
        );
    } catch (e) {
        console.error('加载任务配置选项时发生错误:', e);
        // 清除加载中选项
        $('#' + (selectElementId || 'editTaskConfigId')).find('option[text="加载中..."]').remove();

        // 使用默认选项作为异常处理的后备
        fillDefaultTaskOptions($('#' + (selectElementId || 'editTaskConfigId')));
    }
}

/**
 * 打开添加工作流节点对话框
 * 处理图形模式下添加新节点的功能
 */
window.openAddNodeDialog = function() {
    try {
        console.log('执行openAddNodeDialog函数');

        // 设置模态窗口标题
        $('#workflowNodeEditModalLabel').text(i18n.translate('tasksPage.nodeModal.addNodeTitle', '添加工作流节点'));

        // 设置为新节点模式
        $('#editingNodeArrayIndex').val('-1'); // -1 表示新建节点
        // 显示节点ID输入框（仅在添加新节点时需要）
        $('.node-id-input-group').show();
        $('.existing-node-id-group').hide(); // 隐藏现有节点ID显示组

        // 生成一个默认节点ID
        const timestamp = new Date().getTime();
        const defaultNodeId = 'node_' + timestamp.toString().substring(timestamp.toString().length - 6);
        $('#editNodeId').val(defaultNodeId);

        // 清空名称和参数
        $('#editNodeName').val('');
        $('#editNodeParams').val('{}');

        // 设置显示节点ID
        $('#displayNodeId').text(i18n.translate('tasksPage.nodeModal.newNode', '新节点'));

        // 隐藏删除节点按钮
        $('#deleteNodeBtn').hide();

        // 先清空任务配置选择框，再添加一个初始选项
        const selectElement = $('#editTaskConfigId');
        selectElement.empty();
        selectElement.append(
            $('<option></option>')
                .attr('value', '')
                .text(i18n.translate('tasksPage.nodeModal.selectTaskConfig', '- 选择任务配置 -'))
        );

        // 加载可用的任务配置选项
        loadTaskConfigOptions();

        // 打开模态窗口
        $('#workflowNodeEditModal').modal('show');

        console.log('已打开添加节点对话框');
    } catch (e) {
        console.error('打开添加节点对话框时出错:', e);
        showFeedback(i18n.translate('tasksPage.feedback.errorOpeningNodeDialog', '打开添加节点对话框时出错: ') + e.message, true);
    }
};
