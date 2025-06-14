/**
 * tasks-core.js
 * 任务管理核心功能，包含任务加载、过滤、CRUD操作等
 */

// 避免重复初始化
if (typeof window.tasksCoreInitialized === 'undefined') {
    console.log('初始化tasks core脚本...');
    window.tasksCoreInitialized = true;    // 加载任务列表的函数
    window.loadTasks = function() {
        const filters = {
            taskName: $('#filterTaskName').val().trim(),
            taskGroup: $('#filterTaskGroup').val(),
            taskType: $('#filterTaskType').val() ? parseInt($('#filterTaskType').val()) : null,
            isActive: $('#filterIsActive').val() ? ($('#filterIsActive').val() === 'true') : null
        };
        Object.keys(filters).forEach(key => (filters[key] === null || filters[key] === '' || (typeof filters[key] === 'string' && filters[key].trim() === '')) && delete filters[key]);

        const queryString = $.param(filters);
        const apiUrl = queryString ? `/api/tasks?${queryString}` : '/api/tasks';

        makeApiCall('GET', apiUrl, null,
            function (tasks) {
                const tableBody = $('#tasks-table-body');
                tableBody.empty();

                if (!window.initialGroupPopulationDone && Object.keys(filters).length === 0) { // 只在首次全量加载时填充分组
                    // 如果还没有填充过分组并且没有应用过滤器，获取所有任务以填充分组                    makeApiCall('GET', '/api/tasks', null, function (allTasks) {
                        populateGroupFilter(allTasks);
                    }, function (jqXHR) {
                        console.error("Error fetching all tasks for group population:", jqXHR);
                    });
                }

                tasks.forEach(function (task) {
                    const status = task.active ? `<span class="badge badge-success">${i18n.translate('tasksPage.table.statusActive', 'Active')}</span>` : `<span class="badge badge-secondary">${i18n.translate('tasksPage.table.statusInactive', 'Inactive')}</span>`;
                    const historyBtnText = i18n.translate('tasksPage.table.historyBtnShort', 'History');
                    const editBtnText = i18n.translate('common.edit', 'Edit');
                    const deleteBtnText = i18n.translate('common.delete', 'Delete');
                    const triggerBtnText = i18n.translate('tasksPage.table.triggerBtnShort', 'Trigger');
                    const disableBtnText = i18n.translate('tasksPage.table.disableBtnShort', 'Disable');
                    const enableBtnText = i18n.translate('tasksPage.table.enableBtnShort', 'Enable');

                    const row = `<tr>
                        <td>${task.taskId}</td>
                        <td>${task.taskGroup || ''}</td>
                        <td>${task.taskName || ''}</td>
                        <td>${task.cronExpression || ''}</td>
                        <td>${getTaskTypeString(task.taskType)}</td>
                        <td>${status}</td>
                        <td>${task.executionMode || 'BROADCAST'}</td>
                        <td class="action-buttons">
                            <a href="logs.html?taskId=${task.taskId}" class="btn btn-sm btn-outline-info mr-1" title="${i18n.translate('tasksPage.table.historyBtn', 'View Execution History')}">${historyBtnText}</a>
                            <button class="btn btn-sm btn-info edit-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.editBtn', 'Edit Task')}">${editBtnText}</button>
                            <button class="btn btn-sm btn-danger delete-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.deleteBtn', 'Delete Task')}">${deleteBtnText}</button>
                            <button class="btn btn-sm btn-secondary trigger-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.triggerBtn', 'Trigger Task Manually')}">${triggerBtnText}</button>
                            ${task.active
                            ? `<button class="btn btn-sm btn-warning disable-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.disableBtn', 'Disable Task')}">${disableBtnText}</button>`
                            : `<button class="btn btn-sm btn-success enable-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.enableBtn', 'Enable Task')}">${enableBtnText}</button>`}
                        </td>
                    </tr>`;
                    tableBody.append(row);
                });
            },
            function (jqXHR) {
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingTasks', 'Error loading tasks: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };

    // 填充任务分组过滤器的函数
    window.populateGroupFilter = function(allTasks) {
        const groupSelect = $('#filterTaskGroup');
        groupSelect.find('option:gt(0)').remove(); // 清除除"All Groups"以外的现有分组
        const uniqueGroups = new Set();
        allTasks.forEach(task => {
            if (task.taskGroup && task.taskGroup.trim() !== '') {
                uniqueGroups.add(task.taskGroup.trim());
            }
        });
        // 按字母顺序对分组进行排序，以保持一致的顺序
        Array.from(uniqueGroups).sort().forEach(group => {
            groupSelect.append(`<option value="${group}">${group}</option>`);
        });
        window.initialGroupPopulationDone = true;
    };

    // 获取任务类型字符串
    window.getTaskTypeString = function(typeInt) {
        switch (typeInt) {
            case 0:
                return i18n.translate('tasksPage.modal.taskTypes.bean', 'Bean Task');
            case 2:
                return i18n.translate('tasksPage.modal.taskTypes.http', 'HTTP Task');
            case 4:
                return i18n.translate('tasksPage.modal.taskTypes.shell', 'Shell Script Task');
            case 10:
                return i18n.translate('tasksPage.modal.taskTypes.workflow', 'Workflow Task');
            default:
                return i18n.translate('tasksPage.modal.taskTypes.unknown', 'Unknown ({{typeInt}})').replace('{{typeInt}}', typeInt);
        }
    };    // 加载用户选择框
    window.loadUsersForSelects = function() {
        console.log("loadUsersForSelects: Function called.");
        const successSelect = $('#notifySuccessUserIds');
        const failSelect = $('#notifyFailedUserIds');

        console.log("loadUsersForSelects: Making API call to /api/users");
        makeApiCall('GET', '/api/users', null,
            function (users) {
                console.log("loadUsersForSelects: API call successful. Received users:", users);
                successSelect.empty(); // 清除之前的选项
                failSelect.empty();   // 清除之前的选项

                if (users && Array.isArray(users)) {
                    console.log("loadUsersForSelects: Number of users received:", users.length);
                    let optionsAdded = 0;
                    users.forEach(function (user) {
                        const option = `<option value='${user.userId}'>${user.username}</option>`;
                        successSelect.append(option);
                        failSelect.append(option);
                        optionsAdded++;
                    });
                    console.log("loadUsersForSelects: Options added to dropdowns:", optionsAdded);
                    if (optionsAdded === 0 && users.length > 0) {
                        console.warn("loadUsersForSelects: Users received, but no options added (unexpected).");
                    } else if (users.length === 0) {
                        console.warn("loadUsersForSelects: API returned an empty list of users.");
                    }
                } else {
                    console.warn("loadUsersForSelects: API response was not an array or was null/undefined for users.");
                }
            },
            function (jqXHR) {
                console.error("loadUsersForSelects: API call to /users failed.", jqXHR);
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingUsers', 'Error loading users: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };    // 加载日历选择框
    window.loadCalendarsForSelect = function() {
        makeApiCall('GET', '/api/calendars', null,
            function (calendars) {
                const calendarSelect = $('#taskCalendarGroup');
                calendarSelect.find('option:gt(0)').remove(); // 保留第一个"-- Select Calendar --"选项
                calendars.forEach(function (calendar) {
                    const option = `<option value='${calendar.calendarName}'>${calendar.calendarName}</option>`;
                    calendarSelect.append(option);
                });
            },
            function (jqXHR) {
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingCalendars', 'Error loading calendars: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };

    // 加载可用任务到节点选择下拉框
    window.loadAvailableTasksForNodes = function() {
        console.log("loadAvailableTasksForNodes: Function called.");
        // 检查选择器是否存在
        let select = $('#availableTasksForNodes');
        
        if (select.length === 0) {
            console.log("loadAvailableTasksForNodes: 主选择器未找到，尝试查找其他选择器");
            
            // 查找可能的备选ID（增加图形模式下使用的选择器）
            const possibleSelectIds = [
                '#taskConfig', 
                '#nodeTaskConfig', 
                '#selectTask',
                '#wfTaskSelect',
                '#availableTasks',
                '#nodeTaskSelect',
                '#workflowNodeTaskSelect'
            ];
            
            // 尝试直接通过ID查找
            for (let id of possibleSelectIds) {
                if ($(id).length > 0) {
                    console.log(`loadAvailableTasksForNodes: 找到备选选择器: ${id}`);
                    select = $(id);
                    break;
                }
            }
            
            // 如果仍未找到，尝试使用通配选择器
            if (select.length === 0) {
                const wildcardSelectors = [
                    'select[id*="task"][id*="select"]', 
                    'select[id*="Task"]', 
                    'select.task-select',
                    'select.node-task-select',
                    'select[data-role="task-select"]'
                ];
                
                for (let selector of wildcardSelectors) {
                    if ($(selector).length > 0) {
                        console.log(`loadAvailableTasksForNodes: 找到通配选择器匹配: ${selector}`);
                        select = $(selector).first();
                        break;
                    }
                }
                
                // 最后，尝试查找模态框中的任何选择器
                if (select.length === 0) {
                    const modalSelects = $('.modal:visible select');
                    if (modalSelects.length > 0) {
                        console.log(`loadAvailableTasksForNodes: 在可见模态框中找到选择器`);
                        select = modalSelects.first();
                    }
                }
            }
        }
        
        // 如果仍然未找到任何选择框，创建一个隐藏的备份选择器存储数据
        if (select.length === 0) {
            console.log("loadAvailableTasksForNodes: 未找到任何选择框，创建隐藏的备份选择器");
            $('body').append('<select id="taskBackupSelect" style="display:none;"></select>');
            select = $('#taskBackupSelect');
        }
          const selectId = select.attr('id') || 'unknown';
        console.log(`loadAvailableTasksForNodes: 使用选择框: ${selectId}`);
        
        // 兼容处理：尝试先使用 nodeEditModal 前缀，如果找不到再退回到 workflowTaskFields 前缀
        let placeholderText = i18n.translate('tasksPage.nodeEditModal.selectTaskPlaceholder', null); 
        if (!placeholderText) {
            placeholderText = i18n.translate('tasksPage.modal.workflowTaskFields.selectTaskPlaceholder', '-- 选择任务 --');
            console.log("使用备选翻译键: tasksPage.modal.workflowTaskFields.selectTaskPlaceholder");
        } else {
            console.log("使用主要翻译键: tasksPage.nodeEditModal.selectTaskPlaceholder");
        }        select.empty().append(`<option value="">${placeholderText}</option>`);        console.log("loadAvailableTasksForNodes: Making API call to /api/tasks");
        makeApiCall('GET', '/api/tasks', null,
            function (tasks) {
                console.log("loadAvailableTasksForNodes: API call successful. Received tasks:", tasks);
                if (tasks && Array.isArray(tasks)) {
                    console.log("loadAvailableTasksForNodes: Number of tasks received:", tasks.length);
                    let optionsAdded = 0;
                    tasks.forEach(function (task) {
                        // 确保不选择工作流任务（类型为10）作为节点
                        if ((task.taskType != 10) && task.active) {
                            const optionText = `${task.taskName} (ID: ${task.taskId}, Group: ${task.taskGroup || 'N/A'}, Type: ${getTaskTypeShortString(task.taskType)})`;
                            select.append(`<option value='${task.taskId}'>${optionText}</option>`);
                            optionsAdded++;
                        } else if (task.taskType == 10) {
                            console.log("Excluded workflow task from node selection:", task.taskName, "ID:", task.taskId, "Type:", task.taskType);
                        } else if (!task.active) {
                            console.log("Excluded inactive task from node selection:", task.taskName, "ID:", task.taskId, "Type:", task.taskType);
                        }
                    });
                    console.log("loadAvailableTasksForNodes: Options added to dropdown:", optionsAdded);
                    if (optionsAdded === 0 && tasks.length > 0) {
                        console.warn("loadAvailableTasksForNodes: No tasks met the criteria (type != 10 and isActive) to be added as nodes.");
                    } else if (tasks.length === 0) {
                        console.warn("loadAvailableTasksForNodes: API returned an empty list of tasks.");
                    }
                } else {
                    console.warn("loadAvailableTasksForNodes: API response was not an array or was null/undefined.");
                }
                
                // 查找当前打开的所有模态框中的任务选择下拉框并同步数据
                const modalSelects = $('.modal:visible select');
                if (modalSelects.length > 0) {
                    console.log("loadAvailableTasksForNodes: 找到模态框中的选择框，正在同步数据");
                    modalSelects.each(function() {
                        const $this = $(this);
                        // 检查是否是任务选择下拉框
                        if ($this.attr('id') && 
                            ($this.attr('id').indexOf('task') !== -1 || 
                             $this.attr('id').indexOf('Task') !== -1 || 
                             $this.hasClass('task-select') || 
                             $this.hasClass('node-task-select') ||
                             $this.attr('data-role') === 'task-select')) {
                            
                            // 避免重复处理原始选择器
                            if (!$this.is(select)) {
                                $this.empty().append(select.html());
                                console.log("复制任务选项到:", $this.attr('id') || '未命名选择框');
                            }
                        }
                    });
                }
                
                // 特别处理工作流节点编辑模态框中的选择框
                const nodeEditSelects = $('#workflowNodeEditModal select, #nodeEditModal select');
                if (nodeEditSelects.length > 0) {
                    console.log("loadAvailableTasksForNodes: 找到节点编辑模态框中的选择框，正在同步数据");
                    nodeEditSelects.each(function() {
                        if (!$(this).is(select)) { // 避免重复处理原始选择器
                            $(this).empty().append(select.html());
                            console.log("复制任务选项到节点编辑模态框选择框:", $(this).attr('id') || '未命名选择框');
                        }
                    });
                }

                // 立即应用国际化到刚更新的选择框
                if (typeof i18n !== 'undefined' && typeof i18n.applyTranslations === 'function') {
                    console.log("应用国际化到任务选择框");
                    i18n.applyTranslations(select[0]);
                    i18n.applyTranslations($('.modal:visible')[0]);
                }
            },
            function (jqXHR) {
                console.error("loadAvailableTasksForNodes: API call failed.", jqXHR);
                // 尝试显示错误信息
                select.empty().append(`<option value="" class="text-danger">加载失败 - ${jqXHR.status} ${jqXHR.statusText}</option>`);
            }
        );
    };
    
    // 获取任务类型短字符串（用于选择框显示）
    window.getTaskTypeShortString = function(typeInt) {
        switch (typeInt) {
            case 0:
                return 'Bean';
            case 2:
                return 'HTTP';
            case 4:
                return 'Shell';
            case 10:
                return 'Flow';
            default:
                return `Type${typeInt}`;
        }
    };

    // 加载节点编辑模态框的任务选择列表    window.loadAvailableTasksForEditNode = function() {
        console.log("loadAvailableTasksForEditNode: 正在加载编辑节点的任务选择列表");
        const select = $('#editTaskConfigId');
        const placeholderText = i18n.translate('tasksPage.nodeEditModal.selectTaskPlaceholder', '-- 选择任务 --');
        select.empty().append(`<option value="">${placeholderText}</option>`);

        makeApiCall('GET', '/api/tasks', null,
            function (tasks) {
                console.log("loadAvailableTasksForEditNode: API调用成功，获取任务:", tasks);
                if (tasks && Array.isArray(tasks)) {
                    console.log("loadAvailableTasksForEditNode: 获取任务数量:", tasks.length);
                    let optionsAdded = 0;
                    tasks.forEach(function (task) {
                        // 确保不选择工作流任务（类型为10）作为节点
                        if ((task.taskType != 10) && task.active) {
                            const optionText = `${task.taskName} (ID: ${task.taskId}, Group: ${task.taskGroup || 'N/A'}, Type: ${task.taskType})`;
                            select.append(`<option value='${task.taskId}'>${optionText}</option>`);
                            optionsAdded++;
                        }
                    });
                    console.log("loadAvailableTasksForEditNode: 添加到下拉菜单的选项数:", optionsAdded);
                } else {
                    console.warn("loadAvailableTasksForEditNode: API返回非数组或为空");
                }
            },
            function (jqXHR) {
                console.error("loadAvailableTasksForEditNode: API调用失败", jqXHR);
            }
        );
    };

    // JSON处理辅助函数
    window.parseJsonOrNull = function(jsonString) {
        if (!jsonString || jsonString.trim() === '') {
            return null;
        }
        try {
            return JSON.parse(jsonString);
        } catch (e) {
            showFeedback(i18n.translate('tasksPage.feedback.invalidJson', "Invalid JSON: {{error}}").replace("{{error}}", e.message), true);
            throw e;
        }
    };

    console.log('tasks core脚本初始化完成');
}
