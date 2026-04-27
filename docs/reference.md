# Reference

## Intervals

| Interval | Value |
|----------|-------|
| `1Minute` | 1m |
| `2Minutes` | 2m |
| `5Minutes` | 5m |
| `15Minutes` | 15m |
| `30Minutes` | 30m |
| `60Minutes` | 60m |
| `90Minutes` | 90m |
| `1Hour` | 1h |
| `1Day` | 1d |
| `5Days` | 5d |
| `1Week` | 1wk |
| `1Month` | 1mo |
| `3Months` | 3mo |

## Ranges

| Range | Value |
|-------|-------|
| `1Day` | 1d |
| `5Days` | 5d |
| `1Month` | 1mo |
| `3Months` | 3mo |
| `6Months` | 6mo |
| `1Year` | 1y |
| `2Years` | 2y |
| `5Years` | 5y |
| `10Years` | 10y |
| `YearToDate` | ytd |
| `Max` | max |

## Frequencies

| Frequency | Description |
|-----------|-------------|
| `Yearly` | Annual financial data (up to 4 years of history) |
| `Quarterly` | Quarterly financial data (up to 5 quarters of history) |
| `Trailing` | Trailing twelve months (TTM) data |

## Configuration

**YFinanceClientConfig** - Client configuration:
- `connectTimeout`: HTTP connection timeout
- `readTimeout`: HTTP read timeout
- `retries`: Number of retry attempts for failed requests
- `rateLimit`: Outbound request pacing (default: `RateLimitConfig.Enabled(maxRequestsPerSecond = 2)`)

**RateLimitConfig** - Outbound request pacing applied at the single HTTP chokepoint, shared across all components of one client:
- `Disabled` - no rate limiting
- `Enabled(maxRequestsPerSecond)` - interval-based pacing; request starts are spaced no closer than `1.second / maxRequestsPerSecond` apart, with no burst capacity
- `Default` - `Enabled(maxRequestsPerSecond = 2)`

## Data Models

### Charts

**ChartResult** - Historical OHLCV data and corporate actions:
- `quotes`: List of OHLCV price quotes (datetime, open, high, low, close, volume, adjclose)
- `dividends`: List of dividend events within the chart period
- `splits`: List of stock split events within the chart period
- `corporateActions`: Combined dividends and splits as `CorporateActions`

**DividendEvent** - Dividend payment:
- `exDate`: The ex-dividend date
- `amount`: Dividend amount per share
- `yieldAt(sharePrice)`: Calculate dividend yield

**SplitEvent** - Stock split:
- `exDate`: Effective date of the split
- `numerator`, `denominator`: Split ratio components
- `splitRatio`: Human-readable ratio (e.g., "4:1")
- `factor`: Split factor as decimal
- `isForwardSplit`, `isReverseSplit`: Split direction

**CorporateActions** - Combined dividends and splits:
- `dividends`, `splits`: Event lists
- `totalDividendAmount`, `cumulativeSplitFactor`
- `isEmpty`, `nonEmpty`

**StockResult** - Comprehensive stock information:
- Basic info: symbol, name, exchange, currency
- Price data: current price, change percent, market cap
- Company profile: sector, industry, business summary
- Valuation: P/E ratios, price-to-book, PEG ratio
- Financials: revenue, EBITDA, cash, debt
- Margins: gross, operating, profit, EBITDA
- Growth: earnings growth, revenue growth
- Shares: outstanding, float, short interest

### Options

**OptionChain** - Option chain for a specific expiration date:
- `expirationDate`, `calls`, `puts`, `strikes`, `putCallRatio`
- `itmCalls`, `otmCalls`, `itmPuts`, `otmPuts`, `straddleAtStrike(strike)`

**OptionContract** - Individual option contract:
- `contractSymbol`, `optionType` (Call/Put), `strike`, `expiration`
- `lastPrice`, `bid`, `ask`, `spread`, `midPrice`
- `volume`, `openInterest`, `impliedVolatility`, `inTheMoney`
- `intrinsicValue(price)`, `extrinsicValue(price)`, `notionalValue`

**FullOptionChain** - All expirations:
- `underlyingSymbol`, `underlyingPrice`, `expirationDates`, `chains`
- `nearestExpiration`, `nearestChain`, `chainsWithinDays(days)`

### Holders

**HoldersData** - Comprehensive ownership data:
- `majorHolders`, `institutionalHolders`, `mutualFundHolders`
- `insiderTransactions`, `insiderRoster`
- `netInsiderShares`, `insiderPurchases`, `insiderSales`

**MajorHolders** - Ownership breakdown:
- `insidersPercentHeld`, `institutionsPercentHeld`, `institutionsFloatPercentHeld`, `institutionsCount`

### Financial Statements

**FinancialStatements** - All statements for a company:
- `ticker`, `currency`
- `incomeStatements`, `balanceSheets`, `cashFlowStatements`
- `returnOnAssets`, `returnOnEquity`, `assetTurnover`, `interestCoverage`

**IncomeStatement** - Revenue, operating, and earnings data:
- `totalRevenue`, `costOfRevenue`, `grossProfit`
- `operatingExpense`, `operatingIncome`
- `netIncome`, `ebit`, `ebitda`, `basicEps`, `dilutedEps`

**BalanceSheet** - Assets, liabilities, and equity:
- `totalAssets`, `currentAssets`, `cashAndCashEquivalents`, `inventory`
- `totalLiabilities`, `currentLiabilities`, `totalDebt`
- `stockholdersEquity`, `retainedEarnings`

**CashFlowStatement** - Operating, investing, and financing flows:
- `operatingCashFlow`, `changeInWorkingCapital`
- `investingCashFlow`, `capitalExpenditure`
- `financingCashFlow`, `dividendsPaid`
- `freeCashFlow`

### Analyst Data

**AnalystData** - All analyst data for a security:
- `priceTargets`, `recommendations`, `upgradeDowngradeHistory`
- `earningsEstimates`, `revenueEstimates`, `epsTrends`, `epsRevisions`
- `earningsHistory`, `growthEstimates`
- `currentRecommendation`, `consecutiveBeats`, `earningsBeatRate`

**AnalystPriceTargets** - Consensus targets:
- `currentPrice`, `targetHigh`, `targetLow`, `targetMean`, `targetMedian`
- `numberOfAnalysts`, `recommendationKey`, `recommendationMean`
- `meanUpsidePercent`, `medianUpsidePercent`, `targetRange`

**RecommendationTrend** - Buy/hold/sell summary:
- `period`, `strongBuy`, `buy`, `hold`, `sell`, `strongSell`
- `totalAnalysts`, `totalBullish`, `totalBearish`, `bullishPercent`, `netSentiment`, `isBullish`

**UpgradeDowngrade** - Rating change:
- `date`, `firm`, `toGrade`, `fromGrade`, `action`
- `isUpgrade`, `isDowngrade`, `isInitiation`, `isMaintained`

**EarningsEstimate** / **RevenueEstimate** - Forecasts:
- `period`, `endDate`, `avg`, `low`, `high`, `numberOfAnalysts`, `growth`
- `estimateRange`, `estimateSpreadPercent`, `isQuarterly`, `isYearly`

**EarningsHistory** - Actual vs. estimate:
- `quarter`, `period`, `epsActual`, `epsEstimate`, `epsDifference`, `surprisePercent`
- `isBeat`, `isMiss`, `isMet`

**GrowthEstimates** - Growth with index comparison:
- `period`, `stockGrowth`, `indexGrowth`, `indexSymbol`
- `isOutperforming`, `growthDifferential`

### Sector Data

**SectorData** - Comprehensive sector information:
- `key`, `name`, `symbol`, `overview`
- `topCompanies`, `topETFs`, `topMutualFunds`, `industries`, `researchReports`
- `industryCount`, `totalMarketWeight`, `totalMarketCap`, `description`
- `industriesByWeight`, `findIndustry`, `topCompaniesByWeight`

**SectorKey** - Predefined constants for all 11 GICS sectors (see [Sector Data](sectors.md)).

### Search

**SearchResult** - Search results:
- `quotes` (`QuoteSearchResult`), `news` (`NewsItem`), `lists` (`SearchList`), `totalResults`
- `topQuote`, `equities`, `etfs`, `symbols`, `tickers`

### Screener

**ScreenerResult** - Screener results:
- `quotes` (`ScreenerQuote`), `total`
- `equities`, `funds`, `etfs`, `symbols`, `tickers`, `largestByMarketCap`, `bySector`, `sectors`, `hasMore`

**PredefinedScreen** - Built-in screens (see [Screener](screener.md)).
