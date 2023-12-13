import { useEffect, useState } from "react";
import { backendDeleteApi, backendSaveApi, backendUpdateApi, backendsApi } from "../api/webapp/cluster";
import { Button, ButtonGroup, Form, Modal, Popconfirm, Switch, Table, Typography } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";

export function Cluster() {
  const { Text } = Typography;
  const [backendData, setBackendData] = useState<BackendData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<BackendData>();


  useEffect(() => {
    list()
  }, []);

  const list = () => {
    backendsApi({})
      .then(data => {
        console.log(data)
        setBackendData(data);
      }).catch(() => { });
  }

  const linkRender = (text: any) => {
    return (
      <Text link={{ href: text, target: '_blank' }} underline>{text}</Text>
    );
  }
  const switchRender = (text: any, record: any, index: any) => {
    return (
      <SwitchRender text={text} record={record} index={index} list={list}></SwitchRender>
    );
  }
  const operateRender = (text: any, record: any, index: any) => {
    console.log(text, record, index);
    return (
      <ButtonGroup size={'default'}>
        <Button onClick={() => {
          setForm(record)
          setVisibleForm(true)
        }}>编辑</Button>
        <Popconfirm
          title="确定是否要删除？"
          content="删除改将不可逆"
          position="bottomRight"
          onConfirm={() => {
            backendDeleteApi({ name: record.name })
              .then(data => {
                console.log(data)
                list();
              }).catch(() => { });
          }}
        >
          <Button>删除</Button>
        </Popconfirm>
      </ButtonGroup>
    );
  }

  return (
    <>
      <Table dataSource={backendData} pagination={false} >
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
            console.log(value, record, value === record.routingGroup)
            return value === record.routingGroup
          }} />
        <Column title="ProxyTo" dataIndex="proxyTo" key="proxyTo" render={linkRender} />
        <Column title="ExternalUrl" dataIndex="externalUrl" key="externalUrl" render={linkRender} />
        <Column title="Queued" dataIndex="queued" key="queued" />
        <Column title="Running" dataIndex="running" key="running" />
        <Column title="Active" dataIndex="active" key="active" render={switchRender} />
        <Column title={<>
          <ButtonGroup size={'default'}>
            <Button onClick={() => {
              setForm(undefined)
              setVisibleForm(true)
            }}>新增</Button>
          </ButtonGroup>
        </>} dataIndex="operate" key="operate" render={operateRender} />
      </Table>

      <Modal
        title="自定义样式"
        visible={visibleForm}
        onOk={() => { formApi?.submitForm() }}
        onCancel={() => { setVisibleForm(false) }}
        centered
        bodyStyle={{ overflow: 'auto', height: '80%' }}
      >
        <Form
          labelPosition="left"
          labelAlign="right"
          style={{ padding: '10px', width: 600 }}
          onSubmit={(values) => {
            console.log(values)
            if (form === undefined) {
              backendSaveApi(values)
                .then(data => {
                  console.log(data);
                  list();
                  setVisibleForm(false);
                }).catch(() => { });
            } else {
              backendUpdateApi(values)
                .then(data => {
                  console.log(data);
                  list();
                  setVisibleForm(false);
                }).catch(() => { });
            }
          }}
          getFormApi={setFormApi}
        >
          <Form.Input
            field="name"
            label="Name"
            trigger='blur'
            style={{ width: 200 }}
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
            style={{ width: 200 }}
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
            style={{ width: 200 }}
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
            style={{ width: 200 }}
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
  text: any;
  record: any;
  index: any;
  list: () => void;
}) => {
  const [loading, setLoading] = useState(false);

  const handleSwitchChange = (v: any, e: any) => {
    setLoading(true);
    console.log(v, e, loading, props.text, props.record, props.index);
    props.record.active = v
    backendUpdateApi(props.record)
      .then(data => {
        console.log(data)
        setLoading(false);
        props.list()
      }).catch(() => { });
  };

  return (
    <Switch
      loading={loading}
      onChange={handleSwitchChange}
      checked={props.text}
      aria-label="a switch for demo"
    />
  );
};