import { useEffect, useState } from "react";
import styles from './history.module.scss';
import Locale from "../locales";
import { Button, Card, Form, Table, Tag, Typography } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { queryHistoryApi } from "../api/webapp/history";
import { HistoryData, HistoryDetail } from "../types/history";
import { formatTimestamp } from "../utils/time";
import { backendsApi } from "../api/webapp/cluster";
import { Role, useAccessStore } from "../store";
import { BackendData } from "../types/cluster";

export function History() {
  const { Text } = Typography;
  const access = useAccessStore();
  const [backendData, setBackendData] = useState<BackendData[]>();
  const [historyData, setHistoryData] = useState<HistoryData>();
  const [backendMapping, setBackendMapping] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const [size] = useState(15);
  const [form, setForm] = useState({
    user: access.userName
  });

  useEffect(() => {
    backendsApi({})
      .then(data => {
        setBackendData(data);
        const mapping: Record<string, string> = {};
        for (let index = 0; index < data.length; index++) {
          const backend = data[index] as BackendData;
          mapping[backend.name] = backend.proxyTo;
          mapping[backend.proxyTo] = backend.name;
          mapping[backend.externalUrl] = backend.name;
        }
        setBackendMapping(mapping);
      }).catch(() => { });
  }, []);

  useEffect(() => {
    list(1);
  }, [form]);

  const list = (p: number) => {
    setPage(p);
    queryHistoryApi({
      page: p,
      size: size,
      ...form
    }).then(data => {
      setHistoryData(data);
    }).catch(() => { });
  }

  const linkQueryRender = (text: string, record: HistoryDetail) => {
    return (
      <Text link={{ href: `${record.backendUrl}/ui/query.html?${text}`, target: '_blank' }} underline>{text}</Text>
    );
  }

  const linkRender = (text: string) => {
    return (
      <Text link={{ href: text, target: '_blank' }} underline>{text}</Text>
    );
  }

  const timeRender = (text: number) => {
    return (
      <Text>{formatTimestamp(text)}</Text>
    );
  }

  const ellipsisRender = (text: string) => {
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
              <Form.Select field="backendUrl" label='RoutedTo' style={{ width: 200 }} showClear placeholder={Locale.History.RoutedToTip}>
                {backendData?.map(b => (
                  <Form.Select.Option key={b.proxyTo} value={b.proxyTo}>
                    <Tag color={'blue'} style={{ marginRight: '5px' }}>{backendMapping[b.proxyTo]}</Tag>
                    <Text>{b.proxyTo}</Text>
                  </Form.Select.Option>
                ))}
              </Form.Select>
              <Form.Input field='user' label='User' initValue={form.user} disabled={!access.hasRole(Role.ADMIN)} style={{ width: 150 }} showClear />
              <Form.Input field='queryId' label='QueryId' style={{ width: 260 }} showClear placeholder={Locale.History.QueryIdTip} />
              <Button htmlType='submit' style={{ width: 70 }}>{Locale.UI.Query}</Button>
            </>
          )}
          layout='horizontal'
          onSubmit={(values) => {
            setForm(values)
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
          <Column title="QueryId" dataIndex="queryId" key="queryId" render={linkQueryRender} />
          <Column title="RoutedTo" dataIndex="backendUrl" key="backendUrlName" render={(text: string) => <Text>{backendMapping[text]}</Text>} />
          <Column title="RoutedTo" dataIndex="backendUrl" key="backendUrl" render={linkRender} />
          <Column title="User" dataIndex="user" key="user" />
          <Column title="Source" dataIndex="source" key="source" />
          <Column title="QueryText" dataIndex="queryText" key="queryText" ellipsis={true} width={300} render={ellipsisRender} />
          <Column title="SubmissionTime" dataIndex="captureTime" key="captureTime" render={timeRender} />
        </Table>
      </Card>
    </>
  );
}
