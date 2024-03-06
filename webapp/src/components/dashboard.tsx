import { useEffect, useRef, useState } from "react";
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

export function Dashboard() {
  const access = useAccessStore();
  const navigate = useNavigate();
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
      value: distributionDetail?.startTime
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
              <LineChart data={distributionDetail?.lineChart || {}} />
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

function LineChart(props: {
  data: Record<string, LineChartData[]>
}) {
  const chartRef = useRef(null);

  useEffect(() => {
    const chartInstance = echarts.init(chartRef.current);
    let minMinute = 2400;
    let maxMinute = 0;
    Object.keys(props.data).forEach(d => {
      const lineChartDatas = props.data[d]
      const lineChartDataTemp = lineChartDatas.map(lineChartData => parseInt(lineChartData.minute.replace(":", "")))
      const minMinuteTemp = Math.min(...lineChartDataTemp);
      const maxMinuteTemp = Math.max(...lineChartDataTemp);
      if (minMinuteTemp < minMinute) {
        minMinute = minMinuteTemp;
      }
      if (maxMinuteTemp > maxMinute) {
        maxMinute = maxMinuteTemp;
      }
    });
    const minuteStrings: string[] = [];
    for (let i = minMinute; i <= maxMinute; i++) {
      if ((i % 100) >= 60) {
        continue;
      }
      const hour = Math.floor(i / 100).toString().padStart(2, "0");
      const minute = (i % 100).toString().padStart(2, "0");
      minuteStrings.push(`${hour}:${minute}`);
    }
    const option = {
      legend: {
        textStyle: {
          color: getCSSVar('--semi-color-text-0')
        }
      },
      xAxis: {
        type: 'category',
        data: minuteStrings
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      tooltip: {
        trigger: 'axis'
      },
      series: Object.keys(props.data).map(d => {
        const lineChartDatas = props.data[d].reduce((obj, item) => {
          obj[item.minute] = item.queryCount;
          return obj;
        }, {} as Record<string, any>);
        return {
          name: d,
          data: minuteStrings.map(m => lineChartDatas[m] || 0),
          type: 'line',
          smooth: true
        }
      })
    }
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    chartInstance.setOption(option);
  }, [props.data]);

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

  useEffect(() => {
    const chartInstance = echarts.init(chartRef.current);
    const option = {
      tooltip: {
        trigger: 'item'
      },
      legend: {
        textStyle: {
          color: getCSSVar('--semi-color-text-0')
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
  }, [props.data]);

  return (
    <div style={{ textAlign: "center" }}>
      <div ref={chartRef} style={{ height: "450px" }}></div>
    </div>
  );
}
