import { useEffect, useRef, useState } from "react";

import styles from './dashboard.module.scss';
import * as echarts from "echarts";
import { Card, Col, Descriptions, Row } from "@douyinfe/semi-ui";
import { distributionApi } from "../api/webapp/dashboard";
import { DistributionDetail, DistributionChartData, LineChartData } from "../types/dashboard";
import { getCSSVar } from "../utils/utils";


export function Dashboard() {

  const [distributionDetail, setDistributionDetail] = useState<DistributionDetail>();

  useEffect(() => {
    distributionApi({})
      .then(data => {
        console.log(data)
        setDistributionDetail(data);
      }).catch(() => { });
  }, []);

  const data = [
    {
      key: '启动时间',
      value: distributionDetail?.startTime
    },
    {
      key: '总数',
      value: distributionDetail?.totalBackendCount
    },
    {
      key: '在线',
      value: distributionDetail?.onlineBackendCount,
    },
    {
      key: '下线', value: distributionDetail?.offlineBackendCount
    },
    {
      key: '总查询数（近1小时）', value: distributionDetail?.totalQueryCount
    },
    {
      key: '平均查询数（每分）', value: distributionDetail?.averageQueryCountMinute.toFixed(2)
    },
    {
      key: '平均查询数（每秒）', value: distributionDetail?.averageQueryCountSecond.toFixed(2)
    },
  ];
  return (
    <>
      <div style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col span={24}>
            <Card title='概览' bordered={false} className={styles.card}>
              <Descriptions data={data} row size="large" className={styles.description} />
            </Card>
          </Col>
        </Row>
        <Row gutter={[16, 16]}>
          <Col span={16}>
            <Card title='查询分布（近1小时）' bordered={false} className={styles.card}>
              <LineChart data={distributionDetail?.lineChart || {}} />
            </Card>
          </Col>
          <Col span={8}>
            <Card title='查询分布（近1小时）' bordered={false} className={styles.card}>
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
        minMinute = minMinuteTemp
      }
      if (maxMinuteTemp > maxMinute) {
        maxMinute = maxMinuteTemp
      }
    })
    console.log(minMinute, maxMinute)
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
        const lineChartDatas = props.data[d]
        return {
          name: d,
          data: lineChartDatas.map(lineChartData => lineChartData.queryCount),
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
    </div>)
}

function DistributionChart(props: {
  data: DistributionChartData[]
}) {
  const chartRef = useRef(null);

  useEffect(() => {
    const chartInstance = echarts.init(chartRef.current)
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
          name: '查询数',
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
