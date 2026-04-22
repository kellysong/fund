package com.sjl.fund.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * Excel 导入导出帮助类
 * @author Kelly
 * @version 1.0.0
 */
object ExcelImportHelper {

    private const val TAG = "ExcelImportHelper"

    /**
     * 从Excel文件导入基金代码
     * @param context Context
     * @param uri 文件Uri
     * @return 基金代码列表
     */
    fun importFromExcel(context: Context, uri: Uri): List<String> {
        val fundCodes = mutableListOf<String>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0) // 读取第一个Sheet
            
            Log.d(TAG, "Sheet name: ${sheet.sheetName}, lastRowNum: ${sheet.lastRowNum}")
            
            val dataFormatter = DataFormatter()
            
            // 从第0行开始读取（包含标题行）
            for (i in 0..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                val cell = row.getCell(0) ?: continue
                
                // 使用DataFormatter获取单元格的字符串值（处理数字格式）
                var value = dataFormatter.formatCellValue(cell).trim()
                
                Log.d(TAG, "Row $i, CellType: ${cell.cellType}, Raw value: '$value'")
                
                // 如果DataFormatter返回空，尝试直接读取
                if (value.isEmpty()) {
                    value = when (cell.cellType) {
                        CellType.NUMERIC -> {
                            // 数字类型，转成整数字符串（保留前导零）
                            val numValue = cell.numericCellValue
                            // 检查是否是整数
                            if (numValue == numValue.toLong().toDouble()) {
                                String.format("%06d", numValue.toLong())
                            } else {
                                numValue.toString()
                            }
                        }
                        CellType.STRING -> {
                            cell.stringCellValue?.trim() ?: ""
                        }
                        CellType.FORMULA -> {
                            cell.stringCellValue?.trim() ?: ""
                        }
                        else -> ""
                    }
                }
                
                // 清理值：移除空格、制表符等
                value = value.replace(" ", "").replace("\t", "").replace("\n", "")
                
                Log.d(TAG, "Row $i, Processed value: '$value'")
                
                // 验证是6位数字的基金代码
                if (value.matches(Regex("^\\d{6}$"))) {
                    fundCodes.add(value)
                    Log.d(TAG, "Added fund code: $value")
                } else if (value.isNotEmpty()) {
                    Log.w(TAG, "Invalid fund code format: '$value'")
                }
            }
            
            workbook.close()
        }
        
        Log.d(TAG, "Total imported: ${fundCodes.size} funds")
        return fundCodes
    }

    /**
     * 导出模板文件到应用私有目录（无需存储权限）
     * @param context Context
     * @return 模板文件Uri
     */
    fun exportTemplate(context: Context): Uri {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("基金导入模板")
            
        // 创建标题行
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("基金代码")
            
        // 添加示例数据
        sheet.createRow(1).createCell(0).setCellValue("001970")
        sheet.createRow(2).createCell(0).setCellValue("000001")
        sheet.createRow(3).createCell(0).setCellValue("")
            
        // 设置列宽
        sheet.setColumnWidth(0, 20 * 256)
            
        // 保存到应用私有目录（无需权限）
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "基金导入模板.xlsx")
            
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
            
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileProvider",
            file
        )
    }
}