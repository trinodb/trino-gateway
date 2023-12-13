import { useEffect, useState } from "react";
import { Modal, Table, Typography } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { queryHistoryApi } from "../api/webapp/history";
import { HistoryData } from "../types/history";
import { formatYYYYMMddHHMMSS } from "../utils/time";

export function History() {
  const { Text } = Typography;
  const [historyData, setHistoryData] = useState<HistoryData>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [page, setPage] = useState(1);
  const [size] = useState(15);

  useEffect(() => {
    list(1)
  }, []);

  const list = (p: any) => {
    setPage(p)
    queryHistoryApi({
      page: page,
      size: size
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

  return (
    <>
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
        <Column title="queryText" dataIndex="queryText" key="queryText" />
        <Column title="submissionTime" dataIndex="captureTime" key="captureTime" render={timeRender} />
      </Table>

      <Modal
        title="自定义样式"
        visible={visibleForm}
        onOk={() => { setVisibleForm(false) }}
        centered
        bodyStyle={{ overflow: 'auto', height: '80%' }}
        hasCancel={false}
      >
        xxxx
      </Modal>
    </>
  );
}