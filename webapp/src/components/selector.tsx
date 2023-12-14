import { useEffect, useState } from "react";
import styles from './cluster.module.scss';

import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Table } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { selectorDeleteApi, selectorSaveApi, selectorUpdateApi, selectorsApi } from "../api/webapp/selector";
import { SelectorData } from "../types/selector";

export function Selector() {
  const [selectorData, setResourceGroupData] = useState<SelectorData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<SelectorData>();
  const [useSchema, setUseSchema] = useState<string>();

  useEffect(() => {
    list()
  }, []);

  const list = () => {
    selectorsApi({})
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
            selectorDeleteApi({
              useSchema: useSchema,
              data: record
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
        <Table dataSource={selectorData} pagination={false} rowKey={"resourceGroupId"}>
          <Column title="resourceGroupId" dataIndex="resourceGroupId" key="resourceGroupId" />
          <Column title="priority" dataIndex="priority" key="priority" />
          <Column title="userRegex" dataIndex="userRegex" key="userRegex" />
          <Column title="sourceRegex" dataIndex="sourceRegex" key="sourceRegex" />
          <Column title="queryType" dataIndex="queryType" key="queryType" />
          <Column title="clientTags" dataIndex="clientTags" key="clientTags" />
          <Column title="selectorResourceEstimate" dataIndex="selectorResourceEstimate" key="selectorResourceEstimate" />
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
              selectorSaveApi({
                useSchema: useSchema,
                data: values
              }).then(data => {
                console.log(data);
                list();
                setVisibleForm(false);
              }).catch(() => { });
            } else {
              selectorUpdateApi({
                useSchema: useSchema,
                data: values,
                oldData: form
              }).then(data => {
                console.log(data);
                list();
                setVisibleForm(false);
              }).catch(() => { });
            }
          }}
          getFormApi={setFormApi}
        >
          <Form.InputNumber
            field="resourceGroupId"
            label="resourceGroupId"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.resourceGroupId}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.InputNumber
            field="priority"
            label="priority"
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'number', message: 'type error' },
            ]}
            initValue={form?.priority}
            hideButtons
            formatter={value => `${value}`.replace(/\D/g, '')}
            min={0}
            max={Number.MAX_SAFE_INTEGER}
          />
          <Form.Input
            field="userRegex"
            label="userRegex"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.userRegex}
          />
          <Form.Input
            field="sourceRegex"
            label="sourceRegex"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.sourceRegex}
          />
          <Form.Input
            field="queryType"
            label="queryType"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.queryType}
          />
          <Form.Input
            field="clientTags"
            label="clientTags"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.clientTags}
          />
          <Form.Input
            field="selectorResourceEstimate"
            label="selectorResourceEstimate"
            trigger='blur'
            rules={[
              // { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.selectorResourceEstimate}
          />
        </Form>
      </Modal>
    </>
  );
}