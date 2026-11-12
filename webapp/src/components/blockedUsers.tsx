import { useEffect, useState } from "react";
import styles from './cluster.module.scss';
import Locale from "../locales";
import { blockUserApi, listBlockedUsersApi, unblockUserApi } from "../api/webapp/blockedUsers";
import { backendsApi } from "../api/webapp/cluster";
import { Button, ButtonGroup, Card, Form, Modal, Popconfirm, Table, Toast } from "@douyinfe/semi-ui";
import Column from "@douyinfe/semi-ui/lib/es/table/Column";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { Role, useAccessStore } from "../store";
import { BlockedUser } from "../types/blockedUsers";

export function BlockedUsers() {
  const access = useAccessStore();
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>();
  const [routingGroups, setRoutingGroups] = useState<string[]>([]);
  const [visibleForm, setVisibleForm] = useState(false);
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [scopeType, setScopeType] = useState("GLOBAL");

  useEffect(() => {
    list();
    backendsApi({})
      .then(data => {
        setRoutingGroups([...new Set(data.map(backend => backend.routingGroup))].sort());
      }).catch(() => { });
  }, []);

  const list = () => {
    listBlockedUsersApi({})
      .then(data => {
        setBlockedUsers(data.sort((a, b) => `${a.username}:${a.scopeType}:${a.scopeValue}`.localeCompare(`${b.username}:${b.scopeType}:${b.scopeValue}`)));
      }).catch(() => { Toast.error(Locale.BlockedUsers.ErrorFetch) });
  }

  const operateRender = (_text: any, record: BlockedUser) => {
    return (
      <ButtonGroup size={'default'}>
        <Popconfirm
          title={Locale.BlockedUsers.UnblockTitle}
          content={Locale.BlockedUsers.UnblockContent}
          position="bottomRight"
          onConfirm={() => {
            unblockUserApi({ username: record.username, scopeType: record.scopeType, scopeValue: record.scopeValue })
              .then(() => {
                list();
                Toast.success(Locale.BlockedUsers.Unblock);
              }).catch(() => { Toast.error(Locale.BlockedUsers.ErrorUnblock) });
          }}
        >
          <Button type="danger">{Locale.BlockedUsers.UnblockAction}</Button>
        </Popconfirm>
      </ButtonGroup>
    );
  }

  const dateRender = (text: string) => {
    return text ? String(text) : "";
  }

  return (
    <>
      <Card bordered={false} className={styles.card} bodyStyle={{ padding: '10px' }}>
        <Table dataSource={blockedUsers} pagination={false} rowKey={(record?: BlockedUser) => record ? `${record.username}:${record.scopeType}:${record.scopeValue}` : ""}>
          <Column title={Locale.BlockedUsers.Username} dataIndex="username" key="username"
            sorter={(a, b) => {
              if (!a || !b) return 0;
              return a.username.localeCompare(b.username)
            }} />
          <Column title={Locale.BlockedUsers.Scope} dataIndex="scopeType" key="scopeType" />
          <Column title={Locale.BlockedUsers.ScopeValue} dataIndex="scopeValue" key="scopeValue" />
          <Column title={Locale.BlockedUsers.Reason} dataIndex="reason" key="reason" />
          <Column title={Locale.BlockedUsers.BlockedBy} dataIndex="blockedBy" key="blockedBy" />
          <Column title={Locale.BlockedUsers.BlockedAt} dataIndex="blockedAt" key="blockedAt" render={dateRender} />
          {access.hasRole(Role.ADMIN) && (
            <Column title={<>
              <ButtonGroup size={'default'}>
                <Button onClick={() => {
                  setScopeType("GLOBAL");
                  formApi?.reset();
                  setVisibleForm(true)
                }}>{Locale.BlockedUsers.BlockAction}</Button>
              </ButtonGroup>
            </>} dataIndex="operate" key="operate" render={operateRender} />
          )}
        </Table>
      </Card>
      <Modal
        title={Locale.BlockedUsers.BlockAction}
        visible={visibleForm}
        onOk={() => { formApi?.submitForm() }}
        onCancel={() => { setVisibleForm(false) }}
        centered
        width={500}
        bodyStyle={{ overflow: 'auto' }}
      >
        <Form
          labelPosition="left"
          labelAlign="left"
          labelWidth={150}
          style={{ paddingRight: '20px' }}
          onSubmit={(values) => {
            const request = {
              ...values,
              scopeValue: values.scopeType === "GLOBAL" ? "*" : values.scopeValue,
            };
            blockUserApi(request)
              .then(() => {
                list();
                Toast.success(Locale.BlockedUsers.Block);
                setVisibleForm(false);
                formApi?.reset();
              }).catch(() => { Toast.error(Locale.BlockedUsers.ErrorBlock) });
          }}
          getFormApi={setFormApi}
        >
          <Form.Input
            field="username"
            label={Locale.BlockedUsers.Username}
            trigger='blur'
            rules={[
              { required: true, message: 'required error' },
              { type: 'string', message: 'type error' },
            ]}
          />
          <Form.Select
            field="scopeType"
            label={Locale.BlockedUsers.Scope}
            initValue="GLOBAL"
            onChange={(value) => {
              setScopeType(String(value));
              if (value === "GLOBAL") {
                formApi?.setValue("scopeValue", "*");
              }
              else {
                formApi?.setValue("scopeValue", "");
              }
            }}
          >
            <Form.Select.Option value="GLOBAL">{Locale.BlockedUsers.GlobalScope}</Form.Select.Option>
            <Form.Select.Option value="ROUTING_GROUP">{Locale.BlockedUsers.RoutingGroupScope}</Form.Select.Option>
          </Form.Select>
          <Form.Select
            field="scopeValue"
            label={Locale.BlockedUsers.ScopeValue}
            initValue="*"
            disabled={scopeType === "GLOBAL"}
            rules={[
              { required: scopeType === "ROUTING_GROUP", message: 'required error' },
            ]}
          >
            {routingGroups.map(routingGroup => (
              <Form.Select.Option value={routingGroup} key={routingGroup}>{routingGroup}</Form.Select.Option>
            ))}
          </Form.Select>
          <Form.TextArea
            field="reason"
            label={Locale.BlockedUsers.Reason}
            trigger='blur'
          />
        </Form>
      </Modal>
    </>
  );
}
