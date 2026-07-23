package com.sjl.fund.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.sjl.fund.app.AppConstant
import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
            
            // 从第二行开始读取（跳过表头）
            for (i in 1..sheet.lastRowNum) {
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
     * 导出模板文件到 Download/fund/template
     */
    fun exportTemplate(context: Context): Uri {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("基金导入模板")

        // 表头行
        sheet.createRow(0).createCell(0).setCellValue("基金代码")

        // 示例数据从第二行开始
        sheet.createRow(1).createCell(0).setCellValue("001970")
        sheet.createRow(2).createCell(0).setCellValue("008888")

        sheet.setColumnWidth(0, 20 * 256)

        val dir = File(AppConstant.TEMPLATE_DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "基金导入模板.xlsx")

        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileProvider",
            file
        )
    }

    /**
     * 备份基金数据到 Download/fund/backups
     * 按基金分类（自选/其他）分别创建表格
     */
    fun backupFunds(context: Context): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(AppConstant.BACKUP_DIR)
        if (!dir.exists()) dir.mkdirs()

        val typeNames = mapOf(0 to "自选基金", 1 to "其他基金")

        for ((fundType, typeName) in typeNames) {
            val funds = DaoRepository.listFundInfosByType(fundType) ?: continue
            if (funds.isEmpty()) continue

            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet(typeName)

            // 表头（与列表显示顺序一致）
            val headers = arrayOf("基金代码", "基金名称", "净值日期", "单位净值", "上次涨跌幅", "估值时间", "盘中估值", "盘中涨跌幅")
            val headerRow = sheet.createRow(0)
            for ((i, h) in headers.withIndex()) {
                headerRow.createCell(i).setCellValue(h)
            }

            // 数据行
            for ((idx, f) in funds.withIndex()) {
                val row = sheet.createRow(idx + 1)
                row.createCell(0).setCellValue(f.fundcode)
                row.createCell(1).setCellValue(f.name)
                row.createCell(2).setCellValue(f.jzrq)
                row.createCell(3).setCellValue(f.dwjz)
                row.createCell(4).setCellValue(if (f.jzzzl.endsWith("%")) f.jzzzl else "${f.jzzzl}%")
                row.createCell(5).setCellValue(f.gztime)
                row.createCell(6).setCellValue(f.gsz)
                row.createCell(7).setCellValue(if (f.gszzl.endsWith("%")) f.gszzl else "${f.gszzl}%")
            }

            // 列宽
            for (i in 0..7) sheet.setColumnWidth(i, if (i == 1) 40 * 256 else 15 * 256)

            val file = File(dir, "${typeName}_$dateStr.xlsx")
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
        }

        return dir.path
    }
}