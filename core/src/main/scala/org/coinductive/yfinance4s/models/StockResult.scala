package org.coinductive.yfinance4s.models

final case class StockResult(
    symbol: String,
    longName: String,
    quoteType: String,
    currency: String,
    regularMarketPrice: Double,
    regularMarketChangePercent: Double,
    marketCap: Long,
    exchangeName: String,
    sector: Option[String],
    industry: Option[String],
    longBusinessSummary: Option[String],
    trailingPE: Option[Double],
    forwardPE: Option[Double],
    dividendYield: Option[Double],
    totalCash: Long,
    totalDebt: Long,
    totalRevenue: Long,
    ebitda: Long,
    debtToEquity: Float,
    revenuePerShare: Float,
    returnOnAssets: Float,
    returnOnEquity: Float,
    freeCashflow: Long,
    operatingCashflow: Long,
    earningsGrowth: Float,
    revenueGrowth: Float,
    grossMargins: Float,
    ebitdaMargins: Float,
    operatingMargins: Float,
    profitMargins: Float,
    enterpriseValue: Long,
    floatShares: Long,
    sharesOutstanding: Long,
    sharesShort: Long,
    shortRatio: Double,
    shortPercentOfFloat: Double,
    impliedSharesOutstanding: Long,
    netIncomeToCommon: Double,
    pegRatio: Option[Double],
    enterpriseToRevenue: Float,
    enterpriseToEbitda: Float,
    bookValue: Option[Double],
    priceToBook: Option[Double],
    trailingEps: Option[Double],
    forwardEps: Option[Double]
)
