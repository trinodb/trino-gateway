import { useEffect, useState } from "react";
import styles from './history.module.scss';
import { Button, Card, Form, Table, Typography } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { queryHistoryApi } from "../api/webapp/history";
import { HistoryData } from "../types/history";
import { formatYYYYMMddHHMMSS } from "../utils/time";
import { backendsApi } from "../api/webapp/cluster";

export function History() {
  const { Text } = Typography;
  const [backendData, setBackendData] = useState<BackendData[]>();

  const [historyData, setHistoryData] = useState<HistoryData>();
  const [page, setPage] = useState(1);
  const [size] = useState(15);
  const [form, setForm] = useState({});

  useEffect(() => {
    list(1);
    backendsApi({})
      .then(data => {
        console.log(data)
        setBackendData(data);
      }).catch(() => { });
  }, []);

  useEffect(() => {
    list(1);
  }, [form]);

  const list = (p: any) => {
    setPage(p)
    queryHistoryApi({
      page: p,
      size: size,
      ...form
    })
      .then(data => {
        console.log(data)
        setHistoryData(data);
      }).catch(() => { });
  }

  const linkRender = (text: any) => {
    return (
      <Text link={{ href: `https://localhost:8443/ui/query.html?${text}`, target: '_blank' }} underline>{text}</Text>
    );
  }

  const timeRender = (text: any) => {
    return (
      <Text>{formatYYYYMMddHHMMSS(text)}</Text>
    );
  }

  const ellipsisRender = (text: any) => {
    return (
      <Typography.Text ellipsis={{ showTooltip: true }}>{text}</Typography.Text>
    );
  }

  return (
    <>
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Form labelPosition="left"
          render={() => (
            <>
              <Form.Select field="backendUrl" label='RoutedTo' style={{ width: 200 }} showClear>
                {backendData?.map(b => (
                  <Form.Select.Option value={b.proxyTo}>{b.proxyTo}</Form.Select.Option>
                ))}
              </Form.Select>
              <Form.Input field='user' label='User' style={{ width: 150 }} showClear />
              <Form.Input field='queryId' label='QueryId' style={{ width: 260 }} showClear />
              <Button htmlType='submit' style={{ width: 70 }}>查询</Button>
            </>
          )}
          layout='horizontal'
          onSubmit={(values) => {
            setForm(values)
            console.log(values)
          }}
        ></Form>
      </Card>
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Table dataSource={historyData?.rows} pagination={{
          currentPage: page,
          pageSize: size,
          total: historyData?.total || 0,
          onPageChange: list,
        }}>
          <Column title="queryId" dataIndex="queryId" key="queryId" render={linkRender} />
          <Column title="routedTo" dataIndex="backendUrl" key="backendUrl" />
          <Column title="user" dataIndex="user" key="user" />
          <Column title="source" dataIndex="source" key="source" />
          <Column title="queryText" dataIndex="queryText" key="queryText" ellipsis={true} width={300} render={ellipsisRender} />
          <Column title="submissionTime" dataIndex="captureTime" key="captureTime" render={timeRender} />
        </Table>
      </Card>
    </>
  );
}