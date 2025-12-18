import { useContext, useEffect, useRef, useState } from "react";
import Locale from "../locales";
import styles from './dashboard.module.scss';
import * as echarts from "echarts";
import { Card, Col, Descriptions, Row, Tooltip } from "@douyinfe/semi-ui";
import { distributionApi } from "../api/webapp/dashboard";
import { DistributionDetail, DistributionChartData, LineChartData } from "../types/dashboard";
import { getCSSVar } from "../utils/utils";
import { IconHelpCircle } from "@douyinfe/semi-icons";
import { useNavigate } from "react-router-dom";
import { hasPagePermission, routersMapper } from "../router";
import { useAccessStore } from "../store";
import { formatZonedDateTime, formatZonedTimestamp } from "../utils/time";
import { TimezoneContext } from "./TimezoneContext";

export function Dashboard() {
  const access = useAccessStore();
  const navigate = useNavigate();
  const timezone = useContext(TimezoneContext).timezone;
  const [distributionDetail, setDistributionDetail] = useState<DistributionDetail>();

  useEffect(() => {
    distributionApi({})
      .then(data => {
        setDistributionDetail(data);
      }).catch(() => { });
  }, []);

  const data = [
    {
      key: Locale.Dashboard.StartTime,
      value: distributionDetail && formatZonedDateTime(distributionDetail.startTime, timezone)
    },
    {
      key: Locale.Dashboard.Backends,
      value: hasPagePermission(routersMapper['cluster'], access)
        ? <span className={styles.linkText} onClick={() => {
          const router = routersMapper['cluster'];
          if (router && router.routeProps && router.routeProps.path) {
            navigate(router.routeProps.path);
          }
        }}>{distributionDetail?.totalBackendCount}</span>
        : <span>{distributionDetail?.totalBackendCount}</span>
    },
    {
      key: Locale.Dashboard.BackendsOnline,
      value: distributionDetail?.onlineBackendCount,
    },
    {
      key: Locale.Dashboard.BackendsOffline,
      value: distributionDetail?.offlineBackendCount
    },
    {
      key: Locale.Dashboard.BackendsHealthy,
      value: distributionDetail?.healthyBackendCount,
    },
    {
      key: Locale.Dashboard.BackendsUnhealthy,
      value: distributionDetail?.unhealthyBackendCount
    },
    {
      key: (
        <div className={styles.tip}>
          <span className={styles.title}>{Locale.Dashboard.QPH}</span>
          <span className={styles.help}>
            <Tooltip content={Locale.Dashboard.QPHTip} >
              <IconHelpCircle size="default" />
            </Tooltip>
          </span>
        </div>
      ),
      value: distributionDetail?.totalQueryCount
    },
    {
      key: (
        <div className={styles.tip}>
          <span className={styles.title}>{Locale.Dashboard.QPM}</span>
          <span className={styles.help}>
            <Tooltip content={Locale.Dashboard.QPMTip} >
              <IconHelpCircle size="default" />
            </Tooltip>
          </span>
        </div>
      ),
      value: distributionDetail?.averageQueryCountMinute.toFixed(2)
    },
    {
      key: (
        <div className={styles.tip}>
          <span className={styles.title}>{Locale.Dashboard.QPS}</span>
          <span className={styles.help}>
            <Tooltip content={Locale.Dashboard.QPSTip} >
              <IconHelpCircle size="default" />
            </Tooltip>
          </span>
        </div>
      ),
      value: distributionDetail?.averageQueryCountSecond.toFixed(2)
    },
  ];

  return (
    <>
      <div style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col span={24}>
            <Card title={Locale.Dashboard.Summary} bordered={false} className={styles.card}>
              <Descriptions data={data} row size="large" className={styles.description} />
            </Card>
          </Col>
        </Row>
        <Row gutter={[16, 16]}>
          <Col span={16}>
            <Card title={Locale.Dashboard.QueryDistribution} bordered={false} className={styles.card}>
              <LineChart data={distributionDetail?.lineChart || {}} timeZone={timezone} />
            </Card>
          </Col>
          <Col span={8}>
            <Card title={Locale.Dashboard.QueryDistribution} bordered={false} className={styles.card}>
              <DistributionChart data={distributionDetail?.distributionChart || []} />
            </Card>
          </Col>
        </Row>
      </div>
    </>
  );
}

function updateLegendColor() {
    const [color, setColor] = useState(getCSSVar('--semi-color-text-0'));

    useEffect(() => {
        const handleThemeChange = () => {
            setColor(getCSSVar('--semi-color-text-0'));
        };

        const observer = new MutationObserver(handleThemeChange);
        observer.observe(document.body, { attributes: true, attributeFilter: ["theme-mode"] });
        return () => observer.disconnect();

    }, []);

    return color;
}

function LineChart(props: {
  data: Record<string, LineChartData[]>,
  timeZone: string
}) {
  const chartRef = useRef(null);
  const legendColor = updateLegendColor();

  useEffect(() => {
    const chartInstance = echarts.init(chartRef.current);

    const displayData: Record<string, LineChartData[]> = Object.fromEntries(
      Object.entries(props.data).map(([name, series]) => [
        name,
        series.map((item) => ({
          ...item,
        })),
      ])
    );

    const timestamps = Object.values(displayData).flat().map(d => Number(d.timestamp));
    let minTimestamp = Math.min(...timestamps);
    let maxTimestamp = Math.max(...timestamps);
    const xAxisTimeLabels: number[] = [];
    const MINUTE = 60 * 1000;

    minTimestamp = Math.floor(minTimestamp / MINUTE) * MINUTE;
    maxTimestamp = Math.floor(maxTimestamp / MINUTE) * MINUTE;

    for (let t = minTimestamp; t <= maxTimestamp; t += MINUTE) {
      xAxisTimeLabels.push(t);
    }

    const option = {
      legend: {
        textStyle: {
          color: legendColor
        }
      },
      xAxis: {
        type: 'category',
        data: xAxisTimeLabels.map(ts => formatZonedTimestamp(ts, props.timeZone))
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      tooltip: {
        trigger: 'axis'
      },
      series: Object.keys(displayData).map(d => {
        const data = displayData[d];
        const count = new Map<number, number>();
        for (const dataPoint of data) {
          const xValueHHMM = Math.floor(Number(dataPoint.timestamp) / MINUTE) * MINUTE;
          count.set(xValueHHMM, dataPoint.queryCount);
        }
        return {
          name: d,
          data: xAxisTimeLabels.map(timeStamp => count.get(timeStamp) || 0),
          type: 'line',
          smooth: true
        }
      })
    }
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    chartInstance.setOption(option);
  }, [props.data, props.timeZone, legendColor]);

  return (
    <div style={{ textAlign: "center" }}>
      <div ref={chartRef} style={{ height: "450px" }}></div>
    </div>
  );
}

function DistributionChart(props: {
  data: DistributionChartData[]
}) {
  const chartRef = useRef(null);
  const legendColor = updateLegendColor();

  useEffect(() => {
    const chartInstance = echarts.init(chartRef.current);
    const option = {
      tooltip: {
        trigger: 'item'
      },
      legend: {
        textStyle: {
          color: legendColor
        }
      },
      series: [
        {
          name: Locale.Dashboard.QueryCount,
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center',
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 17,
              fontWeight: 'bold'
            }
          },
          labelLine: {
            show: false
          },
          data: props.data.map(d => {
            return { value: d.queryCount, name: d.name }
          })
        }
      ]
    };
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    chartInstance.setOption(option);
  }, [props.data, legendColor]);

  return (
    <div style={{ textAlign: "center" }}>
      <div ref={chartRef} style={{ height: "450px" }}></div>
    </div>
  );
}
