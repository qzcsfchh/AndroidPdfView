@echo off
color 2f
mode con:cols=80 lines=25

@echo ��ʼ����ļ���
@echo ���build�ļ�...
for /r . %%a in (.) do (
if exist "%%a\build" rd /s /q "%%a\build" 

if exist "%%a\*.iml" del /s /q "%%a\*.iml"

if exist "%%a\local.properties" del /s /q "%%a\local.properties"

if exist "%%a\.gradle" rd /s /q "%%a\.gradle"

if exist "%%a\.idea" rd /s /q "%%a\.idea"

)


rem FOR��4������/d�� /l  �� /r ��  /f
rem /d����ΪĿ¼
rem ���Set (Ҳ����������д��"����ļ�������")����ͨ�����*��?����������Set��ƥ���ÿ��Ŀ¼��������ָ��Ŀ¼�е��ļ��飩ִ��ָ����Command��
rem /R���ݹ�
rem �����Ŀ¼��[Drive:]Path��������ÿ��Ŀ¼��ִ��for��䡣�����/R��û��ָ��Ŀ¼������Ϊ�ǵ�ǰĿ¼�����Setֻ��һ�����(.)����ֻö��Ŀ¼����
rem /L��������ֵ��Χ
rem ʹ�õ�������������ʼֵ(Start#)��Ȼ����ִ��һ�鷶Χ��ֵ��ֱ����ֵ���������õ���ֵֹ(End#)��/L��ͨ����Start#��End#���бȽ���ִ�е���������
rem /f���������ļ�����
rem ʹ���ļ���������������������ַ������ļ����ݡ�ʹ�õ�����������Ҫ�������ݻ��ַ�������ʹ�ø���ParsingKeywordsѡ���һ���޸Ľ�����ʽ��
@echo �ļ�������ɣ�
@pause