package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dto.workflow.WorkflowEdge;
import com.example.taskscheduler.dto.workflow.WorkflowNode;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.util.ExpressionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class WorkflowExecutionServiceTests {

    @Mock
    private TaskConfigDao taskConfigDao;
    @Mock
    private TaskExecuteLogDao taskExecuteLogDao;
    @Mock
    private BeanTaskExecutor beanTaskExecutor;
    @Mock
    private DistributedLockService distributedLockService;
    @Mock
    private ExpressionUtil expressionUtil;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper(); // Real ObjectMapper for JSON parsing/stringifying

    @InjectMocks
    private WorkflowExecutionService workflowExecutionService;

    private TaskConfig workflowTaskConfig;
    private TaskExecuteLog parentWorkflowLog;

    @BeforeEach
    void setUp() {
        workflowTaskConfig = new TaskConfig();
        workflowTaskConfig.setTaskId(100);
        workflowTaskConfig.setTaskName("TestWorkflow");
        workflowTaskConfig.setTaskType(3); // WORKFLOW type
        workflowTaskConfig.setGlobalParametersJson("{\"global_param\":\"global_value\"}");

        parentWorkflowLog = new TaskExecuteLog();
        parentWorkflowLog.setLogId(1);
        parentWorkflowLog.setTaskId(workflowTaskConfig.getTaskId());
        parentWorkflowLog.setState("RUNNING");

        when(distributedLockService.getSchedulerInstanceId()).thenReturn("test-instance");
        // Default mock for log saving
        lenient().when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenAnswer(invocation -> {
            TaskExecuteLog log = invocation.getArgument(0);
            if (log.getLogId() == null) log.setLogId(new java.util.Random().nextInt(1000) + 100); // Assign a dummy ID
            return log;
        });
    }

    private TaskConfig createBeanTaskConfig(Integer id, String name) {
        TaskConfig beanTask = new TaskConfig();
        beanTask.setTaskId(id);
        beanTask.setTaskName(name);
        beanTask.setTaskType(0); // BEAN task
        beanTask.setBeanName("testBean");
        beanTask.setMethodName("testMethod");
        return beanTask;
    }

    @Test
    void testStartWorkflow_NoNodesDefined() throws JsonProcessingException {
        workflowTaskConfig.setWorkflowNodesJson(null); // or "[]"
        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("SUCCESS"), contains("Workflow has no nodes to execute."));
    }
    
    @Test
    void testStartWorkflow_NoEdgesDefined_Fails() throws JsonProcessingException {
        WorkflowNode node1 = new WorkflowNode("node1", 1, "Step 1", null);
        workflowTaskConfig.setWorkflowNodesJson(objectMapper.writeValueAsString(Collections.singletonList(node1)));
        workflowTaskConfig.setWorkflowEdgesJson("[]"); // No edges

        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("FAILED"), contains("Workflow definition incomplete: missing edges."));
    }


    @Test
    void testStartWorkflow_SuccessfulSequentialExecution() throws Exception {
        TaskConfig beanTask1 = createBeanTaskConfig(1, "BeanTask1");
        TaskConfig beanTask2 = createBeanTaskConfig(2, "BeanTask2");

        WorkflowNode node1 = new WorkflowNode("node1", 1, "Step 1", null);
        WorkflowNode node2 = new WorkflowNode("node2", 2, "Step 2", null);
        List<WorkflowNode> nodes = Arrays.asList(node1, node2);
        workflowTaskConfig.setWorkflowNodesJson(objectMapper.writeValueAsString(nodes));

        WorkflowEdge edge1 = new WorkflowEdge("node1", "node2", "SUCCESS", "${node1_status} == 'SUCCESS'", 0);
        List<WorkflowEdge> edges = Collections.singletonList(edge1);
        workflowTaskConfig.setWorkflowEdgesJson(objectMapper.writeValueAsString(edges));

        when(taskConfigDao.findById(1)).thenReturn(Optional.of(beanTask1));
        when(taskConfigDao.findById(2)).thenReturn(Optional.of(beanTask2));

        // Mock expression util for conditions
        when(expressionUtil.evaluate(eq("${node1_status} == 'SUCCESS'"), anyMap())).thenReturn(true);
        // Mock bean executor to do nothing (successful execution)
        doNothing().when(beanTaskExecutor).execute(any(TaskConfig.class));

        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);

        // Verify beanTaskExecutor was called for both tasks
        verify(beanTaskExecutor, times(2)).execute(any(TaskConfig.class));
        // Verify parent workflow log is updated to SUCCESS
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("SUCCESS"), anyString());
        // Verify step logs: 2 saves (initial RUNNING), 2 updates (to SUCCESS)
        verify(taskExecuteLogDao, times(2)).save(argThat(log -> "WORKFLOW_STEP".equals(log.getTaskPattern())));
        verify(taskExecuteLogDao, times(2)).updateLogStatus(anyInt(), eq("SUCCESS"), isNull());
    }

    @Test
    void testWorkflow_NodeFailureStopsWorkflow_IfNoFailurePath() throws Exception {
        TaskConfig beanTask1 = createBeanTaskConfig(1, "BeanTask1");
        WorkflowNode node1 = new WorkflowNode("node1", 1, "Failing Step", null);
        workflowTaskConfig.setWorkflowNodesJson(objectMapper.writeValueAsString(Collections.singletonList(node1)));
        // No edges defined, or edges that don't match FAILURE
        workflowTaskConfig.setWorkflowEdgesJson(objectMapper.writeValueAsString(
            Collections.singletonList(new WorkflowEdge("node1", "node2", "SUCCESS", "${node1_status} == 'SUCCESS'", 0))
        ));


        when(taskConfigDao.findById(1)).thenReturn(Optional.of(beanTask1));
        // Simulate beanTaskExecutor throwing an exception for node1
        doThrow(new RuntimeException("Simulated bean execution failure")).when(beanTaskExecutor).execute(any(TaskConfig.class));

        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);

        verify(beanTaskExecutor, times(1)).execute(any(TaskConfig.class));
        // Parent workflow should be FAILED
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("FAILED"), anyString());
        // Step log for node1 should be FAILED
        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("FAILED"), contains("Simulated bean execution failure"));
    }
    
    @Test
    void testWorkflow_NodeFailureFollowsFailureEdge() throws Exception {
        TaskConfig beanTask1 = createBeanTaskConfig(1, "BeanTask1"); // Failing task
        TaskConfig beanTaskError = createBeanTaskConfig(99, "ErrorHandlerTask"); // Error handler task

        WorkflowNode node1 = new WorkflowNode("node1", 1, "Failing Step", null);
        WorkflowNode nodeError = new WorkflowNode("errorNode", 99, "Error Handler", null);
        List<WorkflowNode> nodes = Arrays.asList(node1, nodeError);
        workflowTaskConfig.setWorkflowNodesJson(objectMapper.writeValueAsString(nodes));

        WorkflowEdge edgeToError = new WorkflowEdge("node1", "errorNode", "FAILURE", "${node1_status} == 'FAILED'", 0);
        List<WorkflowEdge> edges = Collections.singletonList(edgeToError);
        workflowTaskConfig.setWorkflowEdgesJson(objectMapper.writeValueAsString(edges));
        
        when(taskConfigDao.findById(1)).thenReturn(Optional.of(beanTask1));
        when(taskConfigDao.findById(99)).thenReturn(Optional.of(beanTaskError));

        doThrow(new RuntimeException("Failure in node1")).when(beanTaskExecutor).execute(argThat(tc -> tc.getTaskId() == 1));
        doNothing().when(beanTaskExecutor).execute(argThat(tc -> tc.getTaskId() == 99)); // Error handler runs successfully

        // Mock expression util
        when(expressionUtil.evaluate(eq("${node1_status} == 'FAILED'"), anyMap())).thenReturn(true);


        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);

        verify(beanTaskExecutor, times(2)).execute(any(TaskConfig.class)); // node1 and errorNode
        // Parent workflow should be FAILED because a step failed, even if error path executed
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("FAILED"), anyString());
        // Step log for node1 FAILED
        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("FAILED"), contains("Failure in node1"));
        // Step log for errorNode SUCCESS
        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("SUCCESS"), isNull());
    }


    @Test
    void testParameterTemplating_GlobalAndNodeStatus() throws Exception {
        TaskConfig beanTask1 = createBeanTaskConfig(1, "BeanTask1");
        beanTask1.setBeanParameters("{\"base_param\":\"task_default\"}");
        TaskConfig beanTask2 = createBeanTaskConfig(2, "BeanTask2");

        // Node1 has its own parameter, which will be templated
        // Node2's parameters will use status from Node1 and a global parameter
        Map<String, Object> node1Params = new HashMap<>();
        node1Params.put("input_file", "${global_param}/file.txt");
        
        Map<String, Object> node2Params = new HashMap<>();
        node2Params.put("status_from_node1", "${node1_status}");
        node2Params.put("another_input", "${global_param}/other.txt");


        WorkflowNode node1 = new WorkflowNode("node1", 1, "Step 1", node1Params);
        WorkflowNode node2 = new WorkflowNode("node2", 2, "Step 2", node2Params);
        List<WorkflowNode> nodes = Arrays.asList(node1, node2);
        workflowTaskConfig.setWorkflowNodesJson(objectMapper.writeValueAsString(nodes));
        workflowTaskConfig.setGlobalParametersJson("{\"global_param\":\"global_folder\"}");


        WorkflowEdge edge1 = new WorkflowEdge("node1", "node2", "SUCCESS", "${node1_status} == 'SUCCESS'", 0);
        List<WorkflowEdge> edges = Collections.singletonList(edge1);
        workflowTaskConfig.setWorkflowEdgesJson(objectMapper.writeValueAsString(edges));

        when(taskConfigDao.findById(1)).thenReturn(Optional.of(beanTask1));
        when(taskConfigDao.findById(2)).thenReturn(Optional.of(beanTask2));
        doNothing().when(beanTaskExecutor).execute(any(TaskConfig.class));

        // Mock templating and evaluation
        // For node1's parameters:
        when(expressionUtil.resolveTemplates(eq("${global_param}/file.txt"), anyMap()))
            .thenAnswer(inv -> "global_folder/file.txt"); // Simulate resolving global_param
        // For node2's parameters:
        when(expressionUtil.resolveTemplates(eq("${node1_status}"), anyMap()))
            .thenAnswer(inv -> "SUCCESS"); // Simulate node1_status being available
        when(expressionUtil.resolveTemplates(eq("${global_param}/other.txt"), anyMap()))
            .thenAnswer(inv -> "global_folder/other.txt");
        
        // For edge condition:
        when(expressionUtil.evaluate(eq("${node1_status} == 'SUCCESS'"), anyMap())).thenReturn(true);


        workflowExecutionService.startWorkflow(workflowTaskConfig, parentWorkflowLog);

        ArgumentCaptor<TaskConfig> taskConfigCaptor = ArgumentCaptor.forClass(TaskConfig.class);
        verify(beanTaskExecutor, times(2)).execute(taskConfigCaptor.capture());
        List<TaskConfig> executedTasks = taskConfigCaptor.getAllValues();

        // Check parameters for first execution (node1)
        Map<String, Object> paramsForNode1 = objectMapper.readValue(executedTasks.get(0).getBeanParameters(), new TypeReference<Map<String,Object>>(){});
        assertEquals("global_folder/file.txt", paramsForNode1.get("input_file"));
        assertEquals("task_default", paramsForNode1.get("base_param")); // From beanTask1's default

        // Check parameters for second execution (node2)
        Map<String, Object> paramsForNode2 = objectMapper.readValue(executedTasks.get(1).getBeanParameters(), new TypeReference<Map<String,Object>>(){});
        assertEquals("SUCCESS", paramsForNode2.get("status_from_node1"));
        assertEquals("global_folder/other.txt", paramsForNode2.get("another_input"));
        
        verify(taskExecuteLogDao).updateLogStatus(eq(parentWorkflowLog.getLogId()), eq("SUCCESS"), anyString());
    }
}
