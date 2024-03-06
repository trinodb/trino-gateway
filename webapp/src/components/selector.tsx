import { useEffect, useState } from "react";
import styles from './selector.module.scss';
import Locale from "../locales";
import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Table } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { selectorDeleteApi, selectorSaveApi, selectorUpdateApi, selectorsApi } from "../api/webapp/selector";
import { SelectorData } from "../types/selector";
import { Role, useAccessStore } from "../store";

export function Selector() {
  const access = useAccessStore();
  const [selectorData, setSelectorData] = useState<SelectorData[]>();
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [form, setForm] = useState<SelectorData>();
  const [useSchema, setUseSchema] = useState<string>();

  useEffect(() => {
    list();
  }, []);

  const list = () => {
    selectorsApi({})
      .then(data => {
        setSelectorData(data);
      }).catch(() => { });
  }

  const operateRender = (_text: any, record: SelectorData) => {
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
            selectorDeleteApi({
              useSchema: useSchema,
              data: record
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
        <Table dataSource={selectorData} pagination={false} rowKey={"resourceGroupId"}>
          <Column title="ResourceGroupId" dataIndex="resourceGroupId" key="resourceGroupId" />
          <Column title="Priority" dataIndex="priority" key="priority" />
          <Column title="UserRegex" dataIndex="userRegex" key="userRegex" />
          <Column title="SourceRegex" dataIndex="sourceRegex" key="sourceRegex" />
          <Column title="QueryType" dataIndex="queryType" key="queryType" />
          <Column title="ClientTags" dataIndex="clientTags" key="clientTags" />
          <Column title="SelectorResourceEstimate" dataIndex="selectorResourceEstimate" key="selectorResourceEstimate" />
          {access.hasRole(Role.ADMIN) && (
            <Column title={<>
              <ButtonGroup size={'default'}>
                <Button onClick={() => {
                  setForm(undefined);
                  setVisibleForm(true);
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
        width={600}
        height={600}
        bodyStyle={{ overflow: 'auto' }}
      >
        <Form
          labelPosition="left"
          labelAlign="left"
          labelWidth={200}
          style={{ paddingRight: '20px' }}
          onSubmit={(values) => {
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
            label="ResourceGroupId"
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
            label="Priority"
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
            label="UserRegex"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.userRegex}
          />
          <Form.Input
            field="sourceRegex"
            label="SourceRegex"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.sourceRegex}
          />
          <Form.Input
            field="queryType"
            label="QueryType"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.queryType}
          />
          <Form.Input
            field="clientTags"
            label="ClientTags"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.clientTags}
          />
          <Form.Input
            field="selectorResourceEstimate"
            label="SelectorResourceEstimate"
            trigger='blur'
            rules={[
              { type: 'string', message: 'type error' },
            ]}
            initValue={form?.selectorResourceEstimate}
          />
        </Form>
      </Modal>
    </>
  );
}
