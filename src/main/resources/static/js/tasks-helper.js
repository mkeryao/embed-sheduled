/**
 * 这个脚本解决tasks.html页面中的国际化问题
 * 将populateTaskTypeFilter函数移到全局范围内
 */

// 检查页面是否已经加载了此脚本，避免重复执行
if (typeof window.tasksHelperInitialized === 'undefined') {
    console.log('初始化tasks helper脚本...');
    window.tasksHelperInitialized = true;

    // 将populateTaskTypeFilter定义为全局函数
    window.populateTaskTypeFilter = function() {
        console.log('执行populateTaskTypeFilter函数...');
        const typeSelect = $('#filterTaskType');
        // 避免重复填充选项
        if (typeSelect.find('option').length > 1) {
            console.log('任务类型选择器已经填充过，跳过');
            return;
        }

        const taskTypes = [
            {value: "0", key: "tasksPage.modal.taskTypes.bean", defaultText: "Bean Task"},
            {value: "2", key: "tasksPage.modal.taskTypes.http", defaultText: "HTTP Task"},
            {value: "4", key: "tasksPage.modal.taskTypes.shell", defaultText: "Shell Script Task"},
            {value: "10", key: "tasksPage.modal.taskTypes.workflow", defaultText: "Workflow Task"}
        ];
        
        console.log('开始填充任务类型选择器...');
        taskTypes.forEach(type => {
            const translatedText = typeof i18n !== 'undefined' ? i18n.translate(type.key, type.defaultText) : type.defaultText;
            typeSelect.append(`<option value="${type.value}">${translatedText}</option>`);
        });
        console.log('任务类型选择器填充完成');
    };
    
    console.log('tasks helper脚本初始化完成');
}
