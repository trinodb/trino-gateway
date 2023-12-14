import { useEffect, useState } from "react";
import styles from './cluster.module.scss';

import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Table } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { resourceGroupDeleteApi, resourceGroupSaveApi, resourceGroupUpdateApi, resourceGroupsApi } from "../api/webapp/resource-group";
import { ResourceGroupData } from "../types/resource-group";

export function ResourceGroup() {
  const [resourceGroupData, setResourceGroupData] = useState<ResourceGroupData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<ResourceGroupData>();
  const [useSchema, setUseSchema] = useState<string>();

  useEffect(() => {
    list()
  }, []);

  const list = () => {
    resourceGroupsApi({})
      .then(data => {
        console.log(data)
        setResourceGroupData(data);
      }).catch(() => { });
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
            resourceGroupDeleteApi({
              useSchema: useSchema,
              data: {
                resourceGroupId: record.resourceGroupId
              }
            }).then(data => {
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
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Form labelPosition="left"
          render={() => (
            <>
              <Form.Select field="useSchema" label='UseSchema' style={{ width: 200 }} showClear={true} allowCreate={true} filter={true}>
              </Form.Select>
            </>
          )}
          layout='horizontal'
          onValueChange={values => setUseSchema(values.useSchema)}
        ></Form>
      </Card>
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Table dataSource={resourceGroupData} pagination={false} rowKey={"resourceGroupId"}>
          <Column title="resourceGroupId" dataIndex="resourceGroupId" key="resourceGroupId" />
          <Column title="Name" dataIndex="name" key="name" />
          <Column title="parent" dataIndex="parent" key="parent" />
          <Column title="jmxExport" dataIndex="jmxExport" key="jmxExport" />
          <Column title="schedulingPolicy" dataIndex="schedulingPolicy" key="schedulingPolicy" />
          <Column title="schedulingWeight" dataIndex="schedulingWeight" key="schedulingWeight" />
          <Column title="softMemoryLimit" dataIndex="softMemoryLimit" key="softMemoryLimit" />
          <Column title="maxQueued" dataIndex="maxQueued" key="maxQueued" />
          <Column title="hardConcurrencyLimit" dataIndex="hardConcurrencyLimit" key="hardConcurrencyLimit" />
          <Column title="softConcurrencyLimit" dataIndex="softConcurrencyLimit" key="softConcurrencyLimit" />
          <Column title="softCpuLimit" dataIndex="softCpuLimit" key="softCpuLimit" />
          <Column title="hardCpuLimit" dataIndex="hardCpuLimit" key="hardCpuLimit" />
          <Column title="environment" dataIndex="environment" key="environment" />
          <Column title={<>
            <ButtonGroup size={'default'}>
              <Button onClick={() => {
                setForm(undefined)
                setVisibleForm(true)
              }}>新增</Button>
            </ButtonGroup>
          </>} dataIndex="operate" key="operate" fixed="right" render={operateRender} />
        </Table>
      </Card>
      <Modal
        title="自定义样式"
        visible={visibleForm}
        onOk={() => { formApi?.submitForm() }}
        onCancel={() => { setVisibleForm(false) }}
        centered
        width={700}
        height={700}
        bodyStyle={{ overflow: 'auto', height: '80%', width: '600' }}
      >
        <Form
          labelPosition="left"
          labelAlign="left"
          labelWidth={200}
          style={{ paddingRight: '20px' }}
          onSubmit={(values) => {
            console.log(values)
            if (form === undefined) {
              resourceGroupSaveApi({
                useSchema: useSchema,
                data: values
              }).then(data => {
                console.log(data);
                list();
                setVisibleForm(false);
              }).catch(() => { });
            } else {
              resourceGroupUpdateApi({
                useSchema: useSchema,
                data: values
              }).then(data => {
                console.log(data);
                list();
                setVisibleForm(false);
              }).catch(() => { });
            }
          }}
          getFormApi={setFormApi}
        >
          {form !== undefined && (
            <Form.InputNumber
              field="resourceGroupId"
              label="resourceGroupId"
              disabled
              hideButtons
              initValue={form?.resourceGroupId}
            />
          )}
          <Form.Input
            field="name"
            label="Name"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.name}
          />
          <Form.InputNumber
            field="parent"
            label="parent"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.parent}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.Input
            field="jmxExport"
            label="jmxExport"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'boolean', message: 'type error' },
            ]}
            initValue={form?.jmxExport}
          />
          <Form.Input
            field="schedulingPolicy"
            label="schedulingPolicy"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.schedulingPolicy}
          />
          <Form.InputNumber
            field="schedulingWeight"
            label="schedulingWeight"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.schedulingWeight}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.Input
            field="softMemoryLimit"
            label="softMemoryLimit"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.softMemoryLimit}
          />
          <Form.InputNumber
            field="maxQueued"
            label="maxQueued"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.maxQueued}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.InputNumber
            field="hardConcurrencyLimit"
            label="hardConcurrencyLimit"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.hardConcurrencyLimit}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.InputNumber
            field="softConcurrencyLimit"
            label="softConcurrencyLimit"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.softConcurrencyLimit}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.Input
            field="softCpuLimit"
            label="softCpuLimit"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.softCpuLimit}
          />
          <Form.Input
            field="hardCpuLimit"
            label="hardCpuLimit"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.hardCpuLimit}
          />
          <Form.Input
            field="environment"
            label="environment"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.environment}
          />
        </Form>
      </Modal>
    </>
  );
}