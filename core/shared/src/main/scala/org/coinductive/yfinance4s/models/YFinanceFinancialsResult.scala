package org.coinductive.yfinance4s.models

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.deriveDecoder

import java.time.LocalDate
import scala.util.Try

/** Raw API response for financial statements from fundamentals-timeseries endpoint.
  *
  * The decoder transforms the API's metric-first structure into a date-first structure, grouping all metrics by report
  * date and using Circe's automatic derivation.
  */
private[yfinance4s] final case class YFinanceFinancialsResult(
    byDate: Map[LocalDate, RawFinancialData]
)

private[yfinance4s] object YFinanceFinancialsResult {
  private val DefaultPeriodType = "12M"
  private val DefaultCurrency = "USD"
  private val TimescalePrefixPattern = "^(annual|quarterly|trailing)"

  implicit val decoder: Decoder[YFinanceFinancialsResult] = Decoder.instance { c =>
    for {
      result <- c.downField("timeseries").downField("result").as[Option[List[Json]]]
    } yield YFinanceFinancialsResult(parseResult(result.getOrElse(List.empty)))
  }

  private def parseResult(entries: List[Json]): Map[LocalDate, RawFinancialData] = {
    val allValues = entries.flatMap(parseEntry)

    allValues
      .groupBy(_._1)
      .flatMap { case (date, values) =>
        val periodType = values.headOption.map(_._4).getOrElse(DefaultPeriodType)
        val currency = values.headOption.map(_._5).getOrElse(DefaultCurrency)

        val metricsJson = Json.obj(values.map { case (_, key, value, _, _) =>
          toCamelCase(key) -> Json.fromDoubleOrNull(value)
        } *)

        for {
          income <- metricsJson.as[IncomeData].toOption
          balance <- metricsJson.as[BalanceData].toOption
          cashFlow <- metricsJson.as[CashFlowData].toOption
        } yield date -> RawFinancialData(periodType, currency, income, balance, cashFlow)
      }
  }

  private def parseEntry(json: Json): List[(LocalDate, String, Double, String, String)] = {
    val cursor = json.hcursor

    val metricKey = cursor
      .downField("meta")
      .downField("type")
      .as[List[String]]
      .toOption
      .flatMap(_.headOption)
      .map(_.replaceFirst(TimescalePrefixPattern, ""))
      .getOrElse("")

    val dataFieldName = json.asObject
      .flatMap(_.keys.find(k => k != "meta" && k != "timestamp"))

    dataFieldName match {
      case Some(fieldName) =>
        cursor
          .downField(fieldName)
          .as[Option[List[Json]]]
          .toOption
          .flatten
          .getOrElse(List.empty)
          .flatMap { valueJson =>
            val vc = valueJson.hcursor
            for {
              dateStr <- vc.downField("asOfDate").as[String].toOption
              date <- Try(LocalDate.parse(dateStr)).toOption
              rawValue <- vc.downField("reportedValue").downField("raw").as[Double].toOption
              periodType = vc.downField("periodType").as[String].toOption.getOrElse(DefaultPeriodType)
              currency = vc.downField("currencyCode").as[String].toOption.getOrElse(DefaultCurrency)
            } yield (date, metricKey, rawValue, periodType, currency)
          }
      case None => List.empty
    }
  }

  private def toCamelCase(s: String): String =
    if (s.isEmpty) s else s.head.toLower + s.tail
}

/** Container for all financial metrics at a specific reporting date. */
private[yfinance4s] final case class RawFinancialData(
    periodType: String,
    currencyCode: String,
    income: IncomeData,
    balance: BalanceData,
    cashFlow: CashFlowData
)

/** Income statement data fields. */
private[yfinance4s] final case class IncomeData(
    totalRevenue: Option[Double] = None,
    operatingRevenue: Option[Double] = None,
    costOfRevenue: Option[Double] = None,
    grossProfit: Option[Double] = None,
    operatingExpense: Option[Double] = None,
    sellingGeneralAndAdministration: Option[Double] = None,
    researchAndDevelopment: Option[Double] = None,
    depreciationAndAmortizationInIncomeStatement: Option[Double] = None,
    operatingIncome: Option[Double] = None,
    interestIncome: Option[Double] = None,
    interestExpense: Option[Double] = None,
    netInterestIncome: Option[Double] = None,
    otherIncomeExpense: Option[Double] = None,
    pretaxIncome: Option[Double] = None,
    taxProvision: Option[Double] = None,
    netIncome: Option[Double] = None,
    netIncomeContinuousOperations: Option[Double] = None,
    netIncomeCommonStockholders: Option[Double] = None,
    basicEPS: Option[Double] = None,
    dilutedEPS: Option[Double] = None,
    basicAverageShares: Option[Double] = None,
    dilutedAverageShares: Option[Double] = None,
    eBIT: Option[Double] = None,
    eBITDA: Option[Double] = None
)

private[yfinance4s] object IncomeData {
  implicit val decoder: Decoder[IncomeData] = deriveDecoder
}

/** Balance sheet data fields. */
private[yfinance4s] final case class BalanceData(
    totalAssets: Option[Double] = None,
    currentAssets: Option[Double] = None,
    cashAndCashEquivalents: Option[Double] = None,
    otherShortTermInvestments: Option[Double] = None,
    accountsReceivable: Option[Double] = None,
    inventory: Option[Double] = None,
    otherCurrentAssets: Option[Double] = None,
    totalNonCurrentAssets: Option[Double] = None,
    netPPE: Option[Double] = None,
    grossPPE: Option[Double] = None,
    accumulatedDepreciation: Option[Double] = None,
    goodwill: Option[Double] = None,
    otherIntangibleAssets: Option[Double] = None,
    longTermEquityInvestment: Option[Double] = None,
    otherNonCurrentAssets: Option[Double] = None,
    currentLiabilities: Option[Double] = None,
    accountsPayable: Option[Double] = None,
    currentDebt: Option[Double] = None,
    otherCurrentLiabilities: Option[Double] = None,
    totalNonCurrentLiabilitiesNetMinorityInterest: Option[Double] = None,
    longTermDebt: Option[Double] = None,
    otherNonCurrentLiabilities: Option[Double] = None,
    totalLiabilitiesNetMinorityInterest: Option[Double] = None,
    totalDebt: Option[Double] = None,
    netDebt: Option[Double] = None,
    stockholdersEquity: Option[Double] = None,
    commonStock: Option[Double] = None,
    additionalPaidInCapital: Option[Double] = None,
    retainedEarnings: Option[Double] = None,
    treasuryStock: Option[Double] = None,
    shareIssued: Option[Double] = None,
    ordinarySharesNumber: Option[Double] = None,
    workingCapital: Option[Double] = None,
    tangibleBookValue: Option[Double] = None,
    investedCapital: Option[Double] = None
)

private[yfinance4s] object BalanceData {
  implicit val decoder: Decoder[BalanceData] = deriveDecoder
}

/** Cash flow statement data fields. */
private[yfinance4s] final case class CashFlowData(
    operatingCashFlow: Option[Double] = None,
    netIncomeFromContinuingOperations: Option[Double] = None,
    depreciationAmortizationDepletion: Option[Double] = None,
    stockBasedCompensation: Option[Double] = None,
    deferredIncomeTax: Option[Double] = None,
    changeInWorkingCapital: Option[Double] = None,
    changeInReceivables: Option[Double] = None,
    changeInInventory: Option[Double] = None,
    changeInPayable: Option[Double] = None,
    investingCashFlow: Option[Double] = None,
    capitalExpenditure: Option[Double] = None,
    purchaseOfInvestment: Option[Double] = None,
    saleOfInvestment: Option[Double] = None,
    purchaseOfBusiness: Option[Double] = None,
    saleOfBusiness: Option[Double] = None,
    netPPEPurchaseAndSale: Option[Double] = None,
    financingCashFlow: Option[Double] = None,
    issuanceOfDebt: Option[Double] = None,
    repaymentOfDebt: Option[Double] = None,
    issuanceOfCapitalStock: Option[Double] = None,
    repurchaseOfCapitalStock: Option[Double] = None,
    cashDividendsPaid: Option[Double] = None,
    beginningCashPosition: Option[Double] = None,
    endCashPosition: Option[Double] = None,
    changesInCash: Option[Double] = None,
    effectOfExchangeRateChanges: Option[Double] = None,
    freeCashFlow: Option[Double] = None
)

private[yfinance4s] object CashFlowData {
  implicit val decoder: Decoder[CashFlowData] = deriveDecoder
}
