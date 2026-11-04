# 基金列表数据源映射

基金列表主页每一行的数据由 **3 个接口 + 1 个本地数据库** 组合而成。

---

## 数据加载流程

```
requestServer(fundCode)
  ├─ ① getFundInfoV2(fundCode)     → 盘中估值（新浪/fundgz）
  ├─ ② correctNetValueByLsjz(code) → 确认净值（东方财富 lsjz）
  └─ ③ 本地 DB                     → 基金名称、持有信息等
```

每次刷新列表时，每个基金码依次请求接口①和②，①成功后才用②修正，最后合并入库。

---

## 接口①：盘中估值 `getFundInfoV2`

| 属性 | 值 |
|------|-----|
| 接口定义 | `@GET("http://fundgz.1234567.com.cn/js/{code}.js")` |
| 调用位置 | `ApiRepository.getFundInfoV2(fundCode)` |
| 解析方法 | `ApiRepository.applySinaFundInfo(json, fundInfo)` |
| 返回格式 | `var hq_str_fu_xxxxxx="名称,时间,估值,昨收,...,涨跌幅%,日期,..."` (GBK编码CSV) |

### 提取字段

| 原始字段 | 解析位置 | 存入 FundInfo | 列表显示位置 | 含义 |
|----------|----------|--------------|-------------|------|
| `parts[2]` | `applySinaFundInfo:65` | `gsz` | `tv_gz_v1`「今日估值」 | 盘中估算净值 |
| `parts[6]` | `applySinaFundInfo:68` | `gszzl` | `tv_gz_v2`「今日估值」 | 盘中估值涨跌幅(%) |
| `parts[1]` | `applySinaFundInfo:80` | `gztime` | `tv_gz_date`「今日估值」 | 盘中估值时间 (HH:MM) |
| `parts[7]` | `applySinaFundInfo:80` | 拼入 `gztime` | 同上 | 盘中估值日期 (YYYY-MM-DD) |
| `parts[0]` | `applySinaFundInfo:63` | `name`（仅当为空） | `tv_name` | 基金名称（兜底） |

> **注意**：海外/QDII 基金新浪无实时数据，`gsz` 为空时列表显示 `"—"`。

---

## 接口②：确认净值 `getFundHistoryNetValue`

| 属性 | 值 |
|------|-----|
| 接口定义 | `@GET("https://api.fund.eastmoney.com/f10/lsjz")` |
| 调用位置 | `FundListViewModel2.correctNetValueByLsjz(fundCode, fundInfo)` |
| 返回格式 | JSON: `{"Data":{"LSJZList":[{"DWJZ":"","JZZZL":"","FSRQ":""}]}}` |

### 提取字段

| 原始字段 | 解析位置 | 存入 FundInfo | 列表显示位置 | 含义 |
|----------|----------|--------------|-------------|------|
| `DWJZ` | `correctNetValueByLsjz:199` | `dwjz` | `tv_jz_v1`「上次净值」 | 最新确认单位净值 |
| `JZZZL` | `correctNetValueByLsjz:200` | `jzzzl` | `tv_jz_v2`「上次净值」 | 确认净值日涨跌幅(%) |
| `FSRQ` | `correctNetValueByLsjz:201` | `jzrq` | `tv_jz_date`「上次净值」 | 确认净值日期 |

---

## 接口③：基金名称 `getFundNameByCode`

| 属性 | 值 |
|------|-----|
| 接口定义 | `@GET("https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx")` |
| 调用时机 | **仅在添加基金时**（`showAddDialog → searchFund`） |
| 返回格式 | JSON: `{"Datas":[{"NAME":"","FundBaseInfo":{"FTYPE":"","JJGS":"","JJJL":"",...}}]}` |

添加基金时将搜索到的名称存入本地 DB，后续刷新列表时直接从 DB 读取，不再重复调此接口。

---

## 数据类 `FundInfo` 字段总览

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `fundcode` | String | 本地 | 基金代码（主键） |
| `name` | String | DB/① | 添加时存 DB，① 为兜底 |
| `dwjz` | String | ② | 最新确认单位净值 |
| `gsz` | String | ① | 盘中实时估值 |
| `gszzl` | String | ① | 盘中估值涨跌幅(%) |
| `gztime` | String | ① | 盘中估值日期+时间 |
| `jzrq` | String | ② | 确认净值日期 |
| `jzzzl` | String | ②-DB | 确认净值日涨跌幅(%) |
| `holdFlag` | Int | 本地 DB | 0=未持有,1=持有 |
| `holdMoney` | Double | 本地 DB | 持有金额 |
| `fundType` | Int | 本地 DB | 0=自选,1=其他 |
| `sortId` | Int | 本地 | 排序号 |
| `createTime` | Long | 本地 DB | 添加时间戳 |
| `operateType` | Int | @Ignore | 操作类型（瞬态，不落库） |

---

## 列表显示映射

```
┌─────────────────────────────────────────────┐
│  基金名称 (name)                持有标记     │
│  基金代码 (fundcode)                        │
├─────────────────────────────────────────────┤
│  上次净值                                     │
│  日期(jzrq)  │  净值(dwjz)  │  涨跌(jzzzl)  │  ← ② lsjz
├─────────────────────────────────────────────┤
│  今日估值                                     │
│  时间(gztime)│  估值(gsz)   │  涨跌(gszzl)  │  ← ① 新浪
└─────────────────────────────────────────────┘
```

| 列表控件 | 字段 | 接口 |
|----------|------|------|
| `tv_name` | `name` | DB / ① |
| `tv_code` | `fundcode` | 本地 |
| `tv_jz_date` | `jzrq` | ② |
| `tv_jz_v1` | `dwjz` | ② |
| `tv_jz_v2` | `jzzzl` + `%` | ②-DB |
| `tv_gz_date` | `gztime` | ① |
| `tv_gz_v1` | `gsz` | ① |
| `tv_gz_v2` | `gszzl` + `%` | ① |
