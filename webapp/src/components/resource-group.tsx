import { useEffect, useState } from "react";
import styles from './resource-group.module.scss';
import Locale from "../locales";
import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Table } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { resourceGroupDeleteApi, resourceGroupSaveApi, resourceGroupUpdateApi, resourceGroupsApi } from "../api/webapp/resource-group";
import { ResourceGroupData } from "../types/resource-group";
import { Role, useAccessStore } from "../store";

export function ResourceGroup() {
  const access = useAccessStore();
  const [resourceGroupData, setResourceGroupData] = useState<ResourceGroupData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<ResourceGroupData>();
  const [useSchema, setUseSchema] = useState<string>();

  useEffect(() => {
    list();
  }, []);

  const list = () => {
    resourceGroupsApi({})
      .then(data => {
        setResourceGroupData(data);
      }).catch(() => { });
  }

  const operateRender = (_text: any, record: ResourceGroupData) => {
    return (
      <ButtonGroup size={'default'}>
        <Button onClick={() => {
          setForm(record);
          setVisibleForm(true);
        }}>{Locale.UI.Edit}</Button>
        <Popconfirm
          title={Locale.UI.DeleteTitle}
          content={Locale.UI.DeleteContent}
          position="bottomRight"
          onConfirm={() => {
            resourceGroupDeleteApi({
              useSchema: useSchema,
              data: {
                resourceGroupId: record.resourceGroupId
              }
            }).then(data => {
              console.log(data);
              list();
            }).catch(() => { });
          }}
        >
          <Button>{Locale.UI.Delete}</Button>
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
          <Column title="ResourceGroupId" dataIndex="resourceGroupId" key="resourceGroupId" />
          <Column title="Name" dataIndex="name" key="name" />
          <Column title="Parent" dataIndex="parent" key="parent" />
          <Column title="jmxExport" dataIndex="jmxExport" key="jmxExport" />
          <Column title="SchedulingPolicy" dataIndex="schedulingPolicy" key="schedulingPolicy" />
          <Column title="SchedulingWeight" dataIndex="schedulingWeight" key="schedulingWeight" />
          <Column title="SoftMemoryLimit" dataIndex="softMemoryLimit" key="softMemoryLimit" />
          <Column title="MaxQueued" dataIndex="maxQueued" key="maxQueued" />
          <Column title="HardConcurrencyLimit" dataIndex="hardConcurrencyLimit" key="hardConcurrencyLimit" />
          <Column title="SoftConcurrencyLimit" dataIndex="softConcurrencyLimit" key="softConcurrencyLimit" />
          <Column title="SoftCpuLimit" dataIndex="softCpuLimit" key="softCpuLimit" />
          <Column title="HardCpuLimit" dataIndex="hardCpuLimit" key="hardCpuLimit" />
          <Column title="Environment" dataIndex="environment" key="environment" />
          {access.hasRole(Role.ADMIN) && (
            <Column title={<>
              <ButtonGroup size={'default'}>
                <Button onClick={() => {
                  setForm(undefined);
                  setVisibleForm(true);
                }}>{Locale.UI.Create}</Button>
              </ButtonGroup>
            </>} dataIndex="operate" key="operate" fixed="right" render={operateRender} />
          )}
        </Table>
      </Card>
      <Modal
        title={form === undefined ? Locale.UI.Create : Locale.UI.Edit}
        visible={visibleForm}
        onOk={() => { formApi?.submitForm(); }}
        onCancel={() => { setVisibleForm(false); }}
        centered
        width={700}
        height={700}
        bodyStyle={{ overflow: 'auto' }}
      >
        <Form
          labelPosition="left"
          labelAlign="left"
          labelWidth={200}
          style={{ paddingRight: '20px' }}
          onSubmit={(values) => {
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
              label="ResourceGroupId"
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
            label="Parent"
            trigger='blur'
            rules={[
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
              { type: 'boolean', message: 'type error' },
            ]}
            initValue={form?.jmxExport}
          />
          <Form.Input
            field="schedulingPolicy"
            label="SchedulingPolicy"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.schedulingPolicy}
          />
          <Form.InputNumber
            field="schedulingWeight"
            label="SchedulingWeight"
            trigger='blur'
            rules={[
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
            label="SoftMemoryLimit"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.softMemoryLimit}
          />
          <Form.InputNumber
            field="maxQueued"
            label="MaxQueued"
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
            label="HardConcurrencyLimit"
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
            label="SoftConcurrencyLimit"
            trigger='blur'
            rules={[
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
            label="SoftCpuLimit"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.softCpuLimit}
          />
          <Form.Input
            field="hardCpuLimit"
            label="HardCpuLimit"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.hardCpuLimit}
          />
          <Form.Input
            field="environment"
            label="Environment"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.environment}
          />
        </Form>
      </Modal>
    </>
  );
}
