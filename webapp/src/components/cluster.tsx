import { useEffect, useState } from "react";
import styles from './cluster.module.scss';
import Locale from "../locales";
import { backendDeleteApi, backendSaveApi, backendUpdateApi, backendsApi } from "../api/webapp/cluster";
import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Switch, Table, Tag, Toast, Typography } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { Role, useAccessStore } from "../store";
import { BackendData } from "../types/cluster";
import { TagColor } from "@douyinfe/semi-ui/lib/es/tag";

export function Cluster() {
  const { Text } = Typography;
  const access = useAccessStore();
  const [backendData, setBackendData] = useState<BackendData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<BackendData>();

  useEffect(() => {
    list();
  }, []);

  const list = () => {
    backendsApi({})
      .then(data => {
        setBackendData(data.sort((a, b) => a.name.localeCompare(b.name)));
      }).catch(() => { });
  }

  const linkRender = (text: string) => {
    return (
      <Text link={{ href: text, target: '_blank' }} underline>{text}</Text>
    );
  }

  const switchRender = (text: boolean, record: BackendData) => {
    return (
      <SwitchRender text={text} record={record} list={list}></SwitchRender>
    );
  }

  const operateRender = (_text: any, record: BackendData) => {
    return (
      <ButtonGroup size={'default'}>
        <Button onClick={() => {
          setForm(record)
          setVisibleForm(true)
        }}>{Locale.UI.Edit}</Button>
        <Popconfirm
          title={Locale.UI.DeleteTitle}
          content={Locale.UI.DeleteContent}
          position="bottomRight"
          onConfirm={() => {
            backendDeleteApi({ name: record.name })
              .then(() => {
                list();
                Toast.success(Locale.Cluster.Delete);
              }).catch(() => { Toast.error(Locale.Cluster.ErrorDelete) });
          }}
        >
          <Button>{Locale.UI.Delete}</Button>
        </Popconfirm>
      </ButtonGroup>
    );
  }

  const statusRender = (text: string) => {
      let statusColor: TagColor;
      switch (text) {
          case 'HEALTHY':
              statusColor = 'green';
              break;
          case 'UNHEALTHY':
              statusColor = 'red';
              break;
          case 'PENDING':
              statusColor = 'yellow';
              break;
          case 'UNKNOWN':
              statusColor = 'white';
              break;
          default:
              //This should never happen, but just in case defaulting to UNKNOWN state setup as a safety net
              statusColor = 'white';
              break;
        }
        return <Tag color={statusColor}>{text}</Tag>;
    };

  return (
    <>
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Table dataSource={backendData} pagination={false} rowKey={"name"}>
          <Column title="Name" dataIndex="name" key="name" />
          <Column title="routingGroup" dataIndex="routingGroup" key="routingGroup"
            filters={
              [...new Set(backendData?.map(b => b.routingGroup))]
                .map(routingGroup => {
                  return {
                    text: routingGroup,
                    value: routingGroup
                  }
                })}
            onFilter={(value, record) => {
              return value === record.routingGroup
            }} />
          <Column title="ProxyTo" dataIndex="proxyTo" key="proxyTo" render={linkRender} />
          <Column title="ExternalUrl" dataIndex="externalUrl" key="externalUrl" render={linkRender} />
          <Column title="Queued" dataIndex="queued" key="queued" />
          <Column title="Running" dataIndex="running" key="running" />
          <Column title="Active" dataIndex="active" key="active" render={switchRender} />
          <Column title="Status" dataIndex="status" key="status" render={statusRender}/>
          {access.hasRole(Role.ADMIN) && (
            <Column title={<>
              <ButtonGroup size={'default'}>
                <Button onClick={() => {
                  setForm(undefined)
                  setVisibleForm(true)
                }}>{Locale.UI.Create}</Button>
              </ButtonGroup>
            </>} dataIndex="operate" key="operate" render={operateRender} />
          )}
        </Table>
      </Card>
      <Modal
        title={form === undefined ? Locale.UI.Create : Locale.UI.Edit}
        visible={visibleForm}
        onOk={() => { formApi?.submitForm() }}
        onCancel={() => { setVisibleForm(false) }}
        centered
        width={500}
        height={500}
        bodyStyle={{ overflow: 'auto' }}
      >
        <Form
          labelPosition="left"
          labelAlign="left"
          labelWidth={150}
          style={{ paddingRight: '20px' }}
          onSubmit={(values) => {
            if (form === undefined) {
              backendSaveApi(values)
                .then(() => {
                  list();
                  Toast.success(Locale.Cluster.Create);
                  setVisibleForm(false);
                }).catch(() => { Toast.error(Locale.Cluster.ErrorCreate) });
            } else {
              backendUpdateApi(values)
                .then(() => {
                  list();
                  Toast.success(Locale.Cluster.Update);
                  setVisibleForm(false);
                }).catch(() => { Toast.error(Locale.Cluster.ErrorUpdate) });
            }
          }}
          getFormApi={setFormApi}
        >
          <Form.Input
            field="name"
            label="Name"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            disabled={form !== undefined}
            initValue={form?.name}
          />
          <Form.Input
            field="routingGroup"
            label="RoutingGroup"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.routingGroup}
          />
          <Form.Input
            field="proxyTo"
            label="ProxyTo"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.proxyTo}
          />
          <Form.Input
            field="externalUrl"
            label="ExternalUrl"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.externalUrl}
          />
          <Form.Switch label="Active" field='active' initValue={form?.active || false} />
        </Form>
      </Modal>
    </>
  );
}

const SwitchRender = (props: {
  text: boolean;
  record: BackendData;
  list: () => void;
}) => {
  const access = useAccessStore();
  const [loading, setLoading] = useState(false);

  const handleSwitchChange = (v: boolean) => {
    setLoading(true);
    props.record.active = v;
    backendUpdateApi(props.record)
      .then(() => {
        setLoading(false);
        props.list();
      }).catch(() => { });
  };

  return (
    <Switch
      loading={loading}
      onChange={handleSwitchChange}
      checked={props.text}
      disabled={!access.hasRole(Role.ADMIN)}
      aria-label="active switch"
    />
  );
}
