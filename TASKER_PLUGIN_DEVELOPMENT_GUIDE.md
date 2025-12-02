# Tasker 插件开发完整指南

> 本指南适用于在任何 Android 项目中快速集成 Tasker 插件功能。
>
> 官方文档：https://tasker.joaoapps.com/pluginslibrary.html
> 示例项目：https://github.com/joaomgcd/TaskerPluginSample

---

## 目录

1. [环境配置](#1-环境配置)
2. [核心概念](#2-核心概念)
3. [插件类型](#3-插件类型)
4. [Input 和 Output](#4-input-和-output)
5. [完整开发流程](#5-完整开发流程)
6. [常见场景示例](#6-常见场景示例)
7. [最佳实践](#7-最佳实践)
8. [常见问题](#8-常见问题)

---

## 1. 环境配置

### 1.1 添加依赖

在 `app/build.gradle` 中添加：

```gradle
dependencies {
    implementation 'com.joaomgcd:taskerpluginlibrary:0.4.10'

    // 如果使用 Kotlin
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.20"
}
```

### 1.2 添加权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

<!-- 根据需要添加其他权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

---

## 2. 核心概念

Tasker 插件由三个核心组件构成：

```
┌─────────────────┐
│   Config        │  配置界面（Activity）
│   (Activity)    │  - 负责 UI 交互
└────────┬────────┘  - 收集/显示配置数据
         │
         ↓
┌─────────────────┐
│  Config Helper  │  配置助手
│                 │  - 连接 Config 和 Runner
└────────┬────────┘  - 验证输入
         │           - 管理输入/输出
         ↓
┌─────────────────┐
│    Runner       │  执行器
│                 │  - 实际执行业务逻辑
└─────────────────┘  - 返回结果
```

### 2.1 Runner（执行器）

**作用**：实际执行插件的业务逻辑。

**基类选择**：

| 插件类型 | 基类 |
|---------|------|
| Action（动作） | `TaskerPluginRunnerAction` |
| Event（事件） | `TaskerPluginRunnerConditionEvent` |
| State（状态） | `TaskerPluginRunnerConditionState` |

**示例**：

```kotlin
class MyActionRunner : TaskerPluginRunnerActionNoOutputOrInput() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
        // 执行你的业务逻辑
        YourClass.doSomething()

        // 返回成功
        return TaskerPluginResultSucess()

        // 或返回错误
        // return TaskerPluginResultError(1, "Error message")
    }
}
```

**Java 版本**：

```java
public class MyActionRunner extends TaskerPluginRunnerActionNoOutputOrInput {
    @Override
    public TaskerPluginResult<Unit> run(Context context, TaskerInput<Unit> input) {
        YourClass.doSomething();
        return new TaskerPluginResultSucess();
    }
}
```

### 2.2 Config Helper（配置助手）

**作用**：连接 Config 和 Runner，管理输入输出。

**示例**：

```kotlin
class MyActionHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoOutputOrInput<MyActionRunner>(config) {

    override val runnerClass = MyActionRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        // 在 Tasker 中显示的描述文字
        blurbBuilder.append("执行我的动作")
    }
}
```

**Java 版本**：

```java
public class MyActionHelper extends TaskerPluginConfigHelperNoOutputOrInput<MyActionRunner> {
    public MyActionHelper(TaskerPluginConfig<Unit> config) {
        super(config);
    }

    @Override
    public Class<MyActionRunner> getRunnerClass() {
        return MyActionRunner.class;
    }

    @Override
    public void addToStringBlurb(TaskerInput<Unit> input, StringBuilder blurbBuilder) {
        blurbBuilder.append("执行我的动作");
    }
}
```

### 2.3 Config（配置界面）

**作用**：提供用户配置界面。

**示例**（无输入输出的简单版）：

```kotlin
class ActivityConfigMyAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val helper by lazy { MyActionHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }
}
```

**Java 版本**：

```java
public class ActivityConfigMyAction extends Activity implements TaskerPluginConfig<Unit> {
    private MyActionHelper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        helper = new MyActionHelper(this);
        helper.finishForTasker();
    }

    @Override
    public Context getContext() {
        return getApplicationContext();
    }
}
```

---

## 3. 插件类型

### 3.1 Action（动作）

**用途**：执行一个动作（如启动服务、发送请求等）

**AndroidManifest.xml 配置**：

```xml
<activity
    android:name=".tasker.myaction.ActivityConfigMyAction"
    android:exported="true"
    android:label="我的动作"
    android:icon="@mipmap/ic_launcher">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
    </intent-filter>
</activity>
```

**关键点**：
- Intent action: `com.twofortyfouram.locale.intent.action.EDIT_SETTING`
- Runner 基类: `TaskerPluginRunnerAction`

### 3.2 Event（事件）

**用途**：触发一个瞬时事件（如按钮点击、通知到达）

**AndroidManifest.xml 配置**：

```xml
<activity
    android:name=".tasker.myevent.ActivityConfigMyEvent"
    android:exported="true"
    android:label="我的事件"
    android:icon="@mipmap/ic_launcher">
    <intent-filter>
        <action android:name="net.dinglisch.android.tasker.ACTION_EDIT_EVENT" />
    </intent-filter>
</activity>
```

**触发事件**：

```kotlin
// 在你的代码中任何地方触发事件
ActivityConfigMyEvent::class.java.requestQuery(context)

// 或带数据触发
ActivityConfigMyEvent::class.java.requestQuery(context, MyEventData(value))
```

**Java 版本**：

```java
new TaskerPluginResultCondition<>(
    context,
    ActivityConfigMyEvent.class,
    new MyEventData(value)
).signalFinished();
```

**关键点**：
- Intent action: `net.dinglisch.android.tasker.ACTION_EDIT_EVENT`
- Runner 基类: `TaskerPluginRunnerConditionEvent`
- 使用 `requestQuery()` 主动触发

### 3.3 State（状态）

**用途**：查询一个可持续的状态（如灯的开关、播放状态）

**AndroidManifest.xml 配置**：

```xml
<activity
    android:name=".tasker.mystate.ActivityConfigMyState"
    android:exported="true"
    android:label="我的状态"
    android:icon="@mipmap/ic_launcher">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_CONDITION" />
    </intent-filter>
</activity>
```

**Runner 示例**：

```kotlin
class MyStateRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: Unit?
    ): TaskerPluginResultCondition<Unit> {

        // 检查状态
        val isActive = YourClass.checkStatus()

        return if (isActive) {
            TaskerPluginResultConditionSatisfied(context)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
```

**更新状态**：

```kotlin
// 当状态改变时通知 Tasker
ActivityConfigMyState::class.java.requestQuery(context)
```

**关键点**：
- Intent action: `com.twofortyfouram.locale.intent.action.EDIT_CONDITION`
- Runner 基类: `TaskerPluginRunnerConditionState`
- Tasker 会周期性查询状态

---

## 4. Input 和 Output

### 4.1 Input（输入）

**用途**：定义用户可配置的参数。

**规则**：
- 类必须有 `@TaskerInputRoot` 或 `@TaskerInputObject` 注解
- 字段必须有 `@TaskerInputField` 注解
- 支持类型：int, long, float, double, boolean, String, String[], ArrayList<String>, 或嵌套对象
- Kotlin 中必须使用 `var`

**示例**：

```kotlin
@TaskerInputRoot
class MyActionInput @JvmOverloads constructor(
    @field:TaskerInputField("id", labelResIdName = "input_id")
    var id: Int? = null,

    @field:TaskerInputField("name", labelResIdName = "input_name")
    var name: String? = null,

    @field:TaskerInputField("enabled", labelResIdName = "input_enabled")
    var enabled: Boolean = true,

    // 嵌套对象
    @field:TaskerInputObject("options")
    var options: MyOptions = MyOptions()
)

@TaskerInputObject("options", labelResIdName = "input_options")
class MyOptions @JvmOverloads constructor(
    @field:TaskerInputField("timeout", labelResIdName = "input_timeout")
    var timeout: Int = 5000
)
```

**Java 版本**：

```java
@TaskerInputRoot
public class MyActionInput {
    @TaskerInputField(key = "id", labelResIdName = "input_id")
    public Integer id;

    @TaskerInputField(key = "name", labelResIdName = "input_name")
    public String name;

    @TaskerInputField(key = "enabled", labelResIdName = "input_enabled")
    public Boolean enabled = true;

    @TaskerInputObject("options")
    public MyOptions options = new MyOptions();

    public MyActionInput() {}

    public MyActionInput(Integer id, String name, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
    }
}
```

**在 Config 中使用**：

```kotlin
// 从输入填充 UI
override fun assignFromInput(input: TaskerInput<MyActionInput>) {
    editTextId.setText(input.regular.id?.toString())
    editTextName.setText(input.regular.name)
    checkboxEnabled.isChecked = input.regular.enabled
}

// 从 UI 获取输入
override val inputForTasker: TaskerInput<MyActionInput>
    get() {
        val id = editTextId.text.toString().toIntOrNull()
        val name = editTextName.text.toString()
        val enabled = checkboxEnabled.isChecked
        return TaskerInput(MyActionInput(id, name, enabled))
    }
```

**在 Runner 中使用**：

```kotlin
override fun run(context: Context, input: TaskerInput<MyActionInput>): TaskerPluginResult<Unit> {
    val id = input.regular.id ?: return TaskerPluginResultError(1, "ID required")
    val name = input.regular.name
    val enabled = input.regular.enabled

    YourClass.doAction(id, name, enabled)
    return TaskerPluginResultSucess()
}
```

### 4.2 Output（输出）

**用途**：定义返回给 Tasker 的变量。

**规则**：
- 类必须有 `@TaskerOutputObject` 注解
- 方法/属性必须有 `@TaskerOutputVariable` 注解
- 注解应用在 **getter** 上（Kotlin 用 `@get:`）
- 可以嵌套对象
- 数组会自动创建 Tasker 变量数组

**示例**：

```kotlin
@TaskerOutputObject
class MyActionOutput(
    @get:TaskerOutputVariable("result_id", labelResIdName = "output_id")
    val id: Int,

    @get:TaskerOutputVariable("result_name", labelResIdName = "output_name")
    val name: String,

    @get:TaskerOutputVariable("result_time", labelResIdName = "output_time")
    val timestamp: Long = System.currentTimeMillis(),

    // 嵌套对象
    val details: OutputDetails = OutputDetails()
)

@TaskerOutputObject
class OutputDetails(
    @get:TaskerOutputVariable("detail_count", labelResIdName = "output_detail_count")
    val count: Int = 0
)
```

**Java 版本**：

```java
@TaskerOutputObject
public class MyActionOutput {
    @TaskerOutputVariable(value = "result_id", labelResIdName = "output_id")
    public int id;

    @TaskerOutputVariable(value = "result_name", labelResIdName = "output_name")
    public String name;

    @TaskerOutputVariable(value = "result_time", labelResIdName = "output_time")
    public long timestamp;

    public MyActionOutput(int id, String name) {
        this.id = id;
        this.name = name;
        this.timestamp = System.currentTimeMillis();
    }
}
```

**在 Runner 中返回输出**：

```kotlin
override fun run(context: Context, input: TaskerInput<MyActionInput>): TaskerPluginResult<MyActionOutput> {
    val result = YourClass.doAction(input.regular.id)

    val output = MyActionOutput(
        id = result.id,
        name = result.name
    )

    return TaskerPluginResultSucess(output)
}
```

**在 Tasker 中使用**：

用户可以在后续任务中使用这些变量：
- `%result_id`
- `%result_name`
- `%result_time`
- `%detail_count`

### 4.3 动态 Input/Output

**动态输入示例**（在 Helper 中添加）：

```kotlin
override fun addInputs(input: TaskerInputInfos) {
    super.addInputs(input)
    input.add(TaskerInputInfo(
        "timestamp",
        "创建时间",
        null,
        false,
        System.currentTimeMillis()
    ))
}
```

**在 Runner 中获取动态输入**：

```kotlin
val timestamp = input.dynamic.getByKey("timestamp")?.valueAs<Long?>()
```

---

## 5. 完整开发流程

### 场景：创建一个带输入和输出的 Action

#### 步骤 1: 创建文件结构

```
你的项目/
└── tasker/
    └── myaction/
        ├── MyActionInput.kt (或 .java)
        ├── MyActionOutput.kt
        ├── MyActionRunner.kt
        ├── MyActionHelper.kt
        ├── ActivityConfigMyAction.kt
        └── activity_config_myaction.xml
```

#### 步骤 2: 定义 Input 和 Output

**Input**:
```kotlin
@TaskerInputRoot
class MyActionInput @JvmOverloads constructor(
    @field:TaskerInputField("param", labelResIdName = "my_param")
    var param: String? = null
)
```

**Output**:
```kotlin
@TaskerOutputObject
class MyActionOutput(
    @get:TaskerOutputVariable("result", labelResIdName = "my_result")
    val result: String
)
```

#### 步骤 3: 创建 Runner

```kotlin
class MyActionRunner : TaskerPluginRunnerAction<MyActionInput, MyActionOutput>() {
    override fun run(context: Context, input: TaskerInput<MyActionInput>): TaskerPluginResult<MyActionOutput> {
        val param = input.regular.param
            ?: return TaskerPluginResultError(1, "Parameter required")

        // 执行业务逻辑
        val result = YourClass.doSomething(param)

        return TaskerPluginResultSucess(MyActionOutput(result))
    }
}
```

#### 步骤 4: 创建 Helper

```kotlin
class MyActionHelper(config: TaskerPluginConfig<MyActionInput>) :
    TaskerPluginConfigHelper<MyActionInput, MyActionOutput, MyActionRunner>(config) {

    override val runnerClass = MyActionRunner::class.java
    override val inputClass = MyActionInput::class.java
    override val outputClass = MyActionOutput::class.java

    override fun isInputValid(input: TaskerInput<MyActionInput>): Boolean {
        return input.regular.param != null
    }

    override fun addToStringBlurb(input: TaskerInput<MyActionInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("执行: ${input.regular.param}")
    }
}
```

#### 步骤 5: 创建配置 Activity

**布局文件** (`activity_config_myaction.xml`):
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="输入参数:" />

    <EditText
        android:id="@+id/editTextParam"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

</LinearLayout>
```

**Activity**:
```kotlin
class ActivityConfigMyAction : Activity(), TaskerPluginConfig<MyActionInput> {
    private lateinit var editTextParam: EditText
    private lateinit var helper: MyActionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_myaction)

        editTextParam = findViewById(R.id.editTextParam)
        helper = MyActionHelper(this)

        // 加载已有配置
        helper.getPreviousInput()?.regular?.let { input ->
            editTextParam.setText(input.param)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val param = editTextParam.text.toString()
        helper.finishForTasker(TaskerInput(MyActionInput(param)))
    }

    override fun getContext() = applicationContext
}
```

#### 步骤 6: 注册到 AndroidManifest.xml

```xml
<activity
    android:name=".tasker.myaction.ActivityConfigMyAction"
    android:exported="true"
    android:label="我的动作"
    android:icon="@mipmap/ic_launcher">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
    </intent-filter>
</activity>
```

#### 步骤 7: 添加字符串资源

在 `res/values/strings.xml`:
```xml
<string name="my_param">参数</string>
<string name="my_result">结果</string>
```

---

## 6. 常见场景示例

### 6.1 简单 Action（无输入输出）

**完整代码**（一个文件搞定）：

```kotlin
// Runner
class SimpleActionRunner : TaskerPluginRunnerActionNoOutputOrInput() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<Unit> {
        Toast.makeText(context, "执行成功", Toast.LENGTH_SHORT).show()
        return TaskerPluginResultSucess()
    }
}

// Helper
class SimpleActionHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoOutputOrInput<SimpleActionRunner>(config) {
    override val runnerClass = SimpleActionRunner::class.java
    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("简单动作")
    }
}

// Activity
class ActivityConfigSimpleAction : Activity(), TaskerPluginConfigNoInput {
    override val context get() = applicationContext
    private val helper by lazy { SimpleActionHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }
}
```

### 6.2 带后台任务的 Action

```kotlin
class BackgroundActionRunner : TaskerPluginRunnerAction<BackgroundInput, BackgroundOutput>() {

    // 启用后台执行
    override val shouldRunInBackground = true

    override fun run(context: Context, input: TaskerInput<BackgroundInput>): TaskerPluginResult<BackgroundOutput> {
        // 在后台线程执行
        val result = performLongRunningTask()
        return TaskerPluginResultSucess(BackgroundOutput(result))
    }

    private fun performLongRunningTask(): String {
        Thread.sleep(5000)  // 模拟耗时操作
        return "完成"
    }
}
```

### 6.3 触发 Event 并传递数据

**定义 Event Output**:
```kotlin
@TaskerOutputObject
class MyEventOutput(
    @get:TaskerOutputVariable("event_data", labelResIdName = "event_data")
    val data: String
)
```

**Event Runner**:
```kotlin
class MyEventRunner : TaskerPluginRunnerConditionNoInput<MyEventOutput>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: MyEventOutput?
    ): TaskerPluginResultCondition<MyEventOutput> {
        return if (update != null) {
            TaskerPluginResultConditionSatisfied(context, update)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
```

**在代码中触发**:
```kotlin
// 在你的业务代码中
fun triggerMyEvent(context: Context, data: String) {
    ActivityConfigMyEvent::class.java.requestQuery(
        context,
        MyEventOutput(data)
    )
}
```

### 6.4 State 条件判断

```kotlin
class LightStateRunner : TaskerPluginRunnerConditionNoOutputOrInputOrUpdateState() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: Unit?
    ): TaskerPluginResultCondition<Unit> {

        // 查询实际状态
        val isLightOn = YourClass.isLightOn()

        return if (isLightOn) {
            TaskerPluginResultConditionSatisfied(context)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}

// 当灯的状态改变时
fun onLightStateChanged(context: Context) {
    ActivityConfigLightState::class.java.requestQuery(context)
}
```

### 6.5 条件 Event（只在满足条件时触发）

```kotlin
@TaskerInputRoot
class ConditionalEventInput @JvmOverloads constructor(
    @field:TaskerInputField("threshold", labelResIdName = "threshold")
    var threshold: Int = 100
)

@TaskerOutputObject
class ConditionalEventOutput(
    @get:TaskerOutputVariable("value", labelResIdName = "value")
    val value: Int
)

class ConditionalEventRunner : TaskerPluginRunnerCondition<ConditionalEventInput, ConditionalEventOutput>() {
    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<ConditionalEventInput>,
        update: ConditionalEventOutput?
    ): TaskerPluginResultCondition<ConditionalEventOutput> {

        if (update == null) return TaskerPluginResultConditionUnsatisfied()

        // 只在 value 超过阈值时触发
        val threshold = input.regular.threshold
        return if (update.value > threshold) {
            TaskerPluginResultConditionSatisfied(context, update)
        } else {
            TaskerPluginResultConditionUnsatisfied()
        }
    }
}
```

---

## 7. 最佳实践

### 7.1 错误处理

```kotlin
override fun run(context: Context, input: TaskerInput<MyInput>): TaskerPluginResult<MyOutput> {
    try {
        // 1. 参数验证
        val param = input.regular.param
            ?: return TaskerPluginResultError(1, "参数不能为空")

        if (param.length < 3) {
            return TaskerPluginResultError(2, "参数长度至少为 3")
        }

        // 2. 执行业务逻辑
        val result = YourClass.doSomething(param)

        // 3. 返回成功
        return TaskerPluginResultSucess(MyOutput(result))

    } catch (e: IOException) {
        return TaskerPluginResultError(10, "网络错误: ${e.message}")
    } catch (e: Exception) {
        return TaskerPluginResultError(99, "未知错误: ${e.message}")
    }
}
```

**错误码建议**：
- 1-9: 参数错误
- 10-19: 网络错误
- 20-29: 权限错误
- 30-39: 文件/存储错误
- 99: 未知错误

### 7.2 输入验证

```kotlin
override fun isInputValid(input: TaskerInput<MyInput>): Boolean {
    val regular = input.regular

    // 必填字段检查
    if (regular.id == null) return false

    // 范围检查
    if (regular.id!! < 0 || regular.id!! > 1000) return false

    // 字符串检查
    if (regular.name.isNullOrBlank()) return false

    return true
}
```

### 7.3 后台任务

对于耗时操作，启用后台执行：

```kotlin
class LongRunningActionRunner : TaskerPluginRunnerAction<Input, Output>() {

    // 在后台线程运行
    override val shouldRunInBackground = true

    // 显示前台通知
    override val notificationProperties get() = NotificationProperties(
        iconResId = R.drawable.ic_notification,
        titleResId = R.string.notification_title
    )

    override fun run(context: Context, input: TaskerInput<Input>): TaskerPluginResult<Output> {
        // 这里会在后台线程执行
        val result = performLongOperation()
        return TaskerPluginResultSucess(Output(result))
    }
}
```

### 7.4 变量重命名

允许用户自定义输出变量名：

```kotlin
override fun addOutputVariableRenames(
    context: Context,
    input: TaskerInput<MyInput>,
    renames: TaskerOutputRenames
) {
    super.addOutputVariableRenames(context, input, renames)

    // 如果用户指定了自定义变量名
    input.regular.customVarName?.let {
        renames.add(TaskerOutputRename("result", it))
    }
}
```

### 7.5 条件输出

根据配置决定是否输出某些变量：

```kotlin
override fun shouldAddOutput(
    context: Context,
    input: TaskerInput<MyInput>?,
    output: TaskerOuputBase
): Boolean {
    if (input == null) return true

    // 如果用户关闭了详细输出，隐藏某些变量
    if (!input.regular.verboseOutput && output.nameNoSuffix == "detail") {
        return false
    }

    return true
}
```

### 7.6 Tasker 变量

获取 Tasker 的本地变量：

```kotlin
class MyActionHelper(config: TaskerPluginConfig<MyInput>) :
    TaskerPluginConfigHelper<MyInput, MyOutput, MyActionRunner>(config) {

    fun showVariableDialog() {
        val variables = relevantVariables.toList()  // 获取 Tasker 变量列表

        if (variables.isEmpty()) {
            Toast.makeText(config.context, "没有可用变量", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示选择对话框让用户选择变量
        // ...
    }
}
```

---

## 8. 常见问题

### Q1: 如何调试插件？

**答**：
1. 在 Runner 中添加日志：
```kotlin
Log.d("MyPlugin", "Running with input: ${input.regular}")
```

2. 使用 Tasker 的测试功能：
   - 在 Tasker 中配置插件
   - 点击"Test"按钮直接执行

3. 查看 Logcat 输出

### Q2: 插件不显示在 Tasker 中？

**检查清单**：
- [ ] AndroidManifest.xml 中的 `android:exported="true"`
- [ ] Intent-filter 的 action 正确
- [ ] Activity 有 `android:label`
- [ ] 重启 Tasker 应用

### Q3: 配置无法保存？

**可能原因**：
1. `isInputValid()` 返回 false
2. Input 类缺少无参构造函数（Java）
3. Input 字段使用了 `val` 而非 `var`（Kotlin）

### Q4: 如何支持 Tasker 变量替换？

在 Input 中使用 String 类型，Tasker 会自动替换 `%variable` 格式的变量：

```kotlin
@TaskerInputRoot
class MyInput @JvmOverloads constructor(
    @field:TaskerInputField("message", labelResIdName = "message")
    var message: String? = null  // 用户可以输入 "Hello %name"
)

// Runner 中会收到替换后的值
override fun run(context: Context, input: TaskerInput<MyInput>): TaskerPluginResult<Unit> {
    val message = input.regular.message  // 自动替换为 "Hello John"
    // ...
}
```

### Q5: Java 和 Kotlin 可以混用吗？

**答**：完全可以。
- Input/Output 可以用 Java 写
- Runner/Helper 可以用 Kotlin 写
- 或者反过来都行

注意事项：
- Java 调用 Kotlin 的伴生对象：`ClassName.Companion.method()`
- Kotlin 的 `@JvmOverloads` 让 Java 能使用默认参数

### Q6: 如何处理数组输出？

```kotlin
@TaskerOutputObject
class ArrayOutput(
    @get:TaskerOutputVariable("items", labelResIdName = "items")
    val items: Array<String>  // 会自动创建 %items1, %items2, ...
)

// Runner
override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<ArrayOutput> {
    val items = arrayOf("A", "B", "C")
    return TaskerPluginResultSucess(ArrayOutput(items))
}
```

在 Tasker 中使用：
- `%items()` - 数组大小
- `%items1`, `%items2`, `%items3` - 各个元素

### Q7: 如何实现异步操作？

使用协程：

```kotlin
class AsyncActionRunner : TaskerPluginRunnerAction<Input, Output>() {
    override val shouldRunInBackground = true

    override fun run(context: Context, input: TaskerInput<Input>): TaskerPluginResult<Output> {
        return runBlocking {
            try {
                val result = withContext(Dispatchers.IO) {
                    // 异步网络请求
                    performNetworkRequest()
                }
                TaskerPluginResultSucess(Output(result))
            } catch (e: Exception) {
                TaskerPluginResultError(1, e.message ?: "Error")
            }
        }
    }
}
```

---

## 9. 完整示例模板

### 9.1 带输入输出的 Action 模板

复制这个模板，替换 `MyAction` 为你的功能名：

```kotlin
// ========== Input ==========
@TaskerInputRoot
class MyActionInput @JvmOverloads constructor(
    @field:TaskerInputField("param1", labelResIdName = "param1_label")
    var param1: String? = null
)

// ========== Output ==========
@TaskerOutputObject
class MyActionOutput(
    @get:TaskerOutputVariable("result1", labelResIdName = "result1_label")
    val result1: String
)

// ========== Runner ==========
class MyActionRunner : TaskerPluginRunnerAction<MyActionInput, MyActionOutput>() {
    override fun run(context: Context, input: TaskerInput<MyActionInput>): TaskerPluginResult<MyActionOutput> {
        val param1 = input.regular.param1
            ?: return TaskerPluginResultError(1, "param1 required")

        try {
            val result = YourClass.doSomething(param1)
            return TaskerPluginResultSucess(MyActionOutput(result))
        } catch (e: Exception) {
            return TaskerPluginResultError(2, e.message ?: "Error")
        }
    }
}

// ========== Helper ==========
class MyActionHelper(config: TaskerPluginConfig<MyActionInput>) :
    TaskerPluginConfigHelper<MyActionInput, MyActionOutput, MyActionRunner>(config) {

    override val runnerClass = MyActionRunner::class.java
    override val inputClass = MyActionInput::class.java
    override val outputClass = MyActionOutput::class.java

    override fun isInputValid(input: TaskerInput<MyActionInput>): Boolean {
        return input.regular.param1 != null
    }

    override fun addToStringBlurb(input: TaskerInput<MyActionInput>, blurbBuilder: StringBuilder) {
        blurbBuilder.append("MyAction: ${input.regular.param1}")
    }
}

// ========== Activity ==========
class ActivityConfigMyAction : Activity(), TaskerPluginConfig<MyActionInput> {
    private lateinit var editTextParam1: EditText
    private lateinit var helper: MyActionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_myaction)

        editTextParam1 = findViewById(R.id.editTextParam1)
        helper = MyActionHelper(this)

        helper.getPreviousInput()?.regular?.let {
            editTextParam1.setText(it.param1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val input = MyActionInput(editTextParam1.text.toString())
        helper.finishForTasker(TaskerInput(input))
    }

    override fun getContext() = applicationContext
}
```

**对应的布局文件** (`activity_config_myaction.xml`):

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="参数 1:" />

    <EditText
        android:id="@+id/editTextParam1"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

</LinearLayout>
```

**AndroidManifest.xml**:

```xml
<activity
    android:name=".tasker.myaction.ActivityConfigMyAction"
    android:exported="true"
    android:label="My Action"
    android:icon="@mipmap/ic_launcher">
    <intent-filter>
        <action android:name="com.twofortyfouram.locale.intent.action.EDIT_SETTING" />
    </intent-filter>
</activity>
```

**strings.xml**:

```xml
<string name="param1_label">参数 1</string>
<string name="result1_label">结果 1</string>
```

---

## 10. 快速检查清单

开发完成后，使用这个清单确保一切正确：

### 配置阶段
- [ ] build.gradle 添加了依赖
- [ ] AndroidManifest.xml 添加了权限
- [ ] AndroidManifest.xml 注册了 Activity
- [ ] Intent-filter action 正确（EDIT_SETTING/ACTION_EDIT_EVENT/EDIT_CONDITION）
- [ ] Activity 有 `android:exported="true"`
- [ ] Activity 有 `android:label`

### Input/Output
- [ ] Input 类有 `@TaskerInputRoot` 注解
- [ ] Input 字段有 `@TaskerInputField` 注解
- [ ] Output 类有 `@TaskerOutputObject` 注解
- [ ] Output 属性有 `@TaskerOutputVariable` 注解（注意 Kotlin 用 `@get:`）
- [ ] Kotlin 中 Input 字段用 `var`
- [ ] Java 中 Input 类有无参构造函数

### Runner/Helper/Config
- [ ] Runner 继承了正确的基类
- [ ] Helper 继承了正确的基类
- [ ] Helper 中指定了正确的 Runner/Input/Output 类
- [ ] Config 实现了 `TaskerPluginConfig` 接口
- [ ] Config 中实现了 `getContext()` 方法
- [ ] Activity 中调用了 `helper.finishForTasker()`

### 功能测试
- [ ] 在 Tasker 中能找到插件
- [ ] 配置界面能正常打开
- [ ] 配置能正常保存
- [ ] 执行时能调用到业务逻辑
- [ ] 输出变量在 Tasker 中可用
- [ ] 错误处理正常

---

## 附录 A：类继承关系图

```
Action:
  TaskerPluginRunnerAction<Input, Output>
    ├─ TaskerPluginRunnerActionNoOutput<Input>
    └─ TaskerPluginRunnerActionNoOutputOrInput

Event:
  TaskerPluginRunnerConditionEvent<Input, Output>
    ├─ TaskerPluginRunnerConditionEventNoInput<Output>
    └─ TaskerPluginRunnerConditionEventNoOutputOrInput

State:
  TaskerPluginRunnerConditionState<Input, Output>
    ├─ TaskerPluginRunnerConditionStateNoInput<Output>
    └─ TaskerPluginRunnerConditionStateNoOutputOrInputOrUpdate

Config Helper:
  TaskerPluginConfigHelper<Input, Output, Runner>
    ├─ TaskerPluginConfigHelperNoOutput<Input, Runner>
    ├─ TaskerPluginConfigHelperNoInput<Output, Runner>
    └─ TaskerPluginConfigHelperNoOutputOrInput<Runner>
```

---

## 附录 B：给 AI 的提示词模板

当你需要 AI 帮你生成 Tasker 插件代码时，可以使用这个模板：

```
我需要创建一个 Tasker 插件，需求如下：

**插件类型**：Action / Event / State（选一个）

**功能描述**：[简要描述插件要做什么]

**输入参数**：
- 参数1：类型，说明
- 参数2：类型，说明

**输出变量**：
- 变量1：类型，说明
- 变量2：类型，说明

**业务逻辑**：
[描述插件执行时要做什么]

请根据 D:\my_first_web\TaskerPluginSample\TASKER_PLUGIN_DEVELOPMENT_GUIDE.md 文档，
生成完整的代码，包括：
1. Input 类
2. Output 类
3. Runner 类
4. Helper 类
5. Activity 类
6. 布局文件 XML
7. AndroidManifest.xml 配置
8. strings.xml 资源
```

**示例**：

```
我需要创建一个 Tasker 插件，需求如下：

**插件类型**：Action

**功能描述**：发送 HTTP GET 请求并返回响应内容

**输入参数**：
- url: String，要请求的 URL
- timeout: Int，超时时间（秒）

**输出变量**：
- response: String，响应内容
- statusCode: Int，HTTP 状态码

**业务逻辑**：
使用 OkHttp 发送 GET 请求到指定 URL，设置超时时间，返回响应内容和状态码

请根据 TASKER_PLUGIN_DEVELOPMENT_GUIDE.md 文档生成完整代码。
```

---

## 结语

这份文档涵盖了 Tasker 插件开发的所有核心知识点。建议：

1. **新手**：从"快速开始"的简单示例开始
2. **进阶**：参考"完整开发流程"创建带输入输出的插件
3. **高级**：查看"常见场景示例"了解各种高级用法

开发时遇到问题，可以：
- 查看官方示例：https://github.com/joaomgcd/TaskerPluginSample
- 参考本文档的"常见问题"章节
- 使用"快速检查清单"排查问题

祝你开发顺利！🎉
