@echo off
color 2f
mode con:cols=80 lines=25

@echo 开始清除文件：
@echo 清除build文件...
for /r . %%a in (.) do (
if exist "%%a\build" rd /s /q "%%a\build" 

if exist "%%a\*.iml" del /s /q "%%a\*.iml"

if exist "%%a\local.properties" del /s /q "%%a\local.properties"

if exist "%%a\.gradle" rd /s /q "%%a\.gradle"

if exist "%%a\.idea" rd /s /q "%%a\.idea"

)


rem FOR有4个参数/d、 /l  、 /r 、  /f
rem /d：仅为目录
rem 如果Set (也就是我上面写的"相关文件或命令")包含通配符（*和?），将对与Set相匹配的每个目录（而不是指定目录中的文件组）执行指定的Command。
rem /R：递归
rem 进入根目录树[Drive:]Path，在树的每个目录中执行for语句。如果在/R后没有指定目录，则认为是当前目录。如果Set只是一个句点(.)，则只枚举目录树。
rem /L：迭代数值范围
rem 使用迭代变量设置起始值(Start#)，然后逐步执行一组范围的值，直到该值超过所设置的终止值(End#)。/L将通过对Start#与End#进行比较来执行迭代变量。
rem /f：迭代及文件解析
rem 使用文件解析来处理命令输出、字符串及文件内容。使用迭代变量定义要检查的内容或字符串，并使用各种ParsingKeywords选项进一步修改解析方式。
@echo 文件清理完成！
@pause