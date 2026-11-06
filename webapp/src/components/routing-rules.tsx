import { useEffect, useState } from "react";
import { createRoutingRuleApi, deleteRoutingRuleApi, routingRulesApi, updateRoutingRulesApi } from "../api/webapp/routing-rules.ts";
import { RoutingRulesData, RoutingRuleStatus } from "../types/routing-rules";
import { Banner, Button, Card, Form, Modal, Popconfirm, Space, Tag, Toast, Spin } from "@douyinfe/semi-ui";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { Role, useAccessStore } from "../store";
import Locale from "../locales";

const parseActions = (text: string): string[] =>
        text.split("\n")
                .map(action => action.trim())
                .filter(action => action.length > 0);

const required = {required: true, message: 'required error'} as const;
const stringType = {type: 'string', message: 'type error'} as const;

interface RoutingRuleFieldsProps {
    rule?: RoutingRulesData;
    nameDisabled: boolean;
    disabled: boolean;
    width: number;
}

// Shared by the Add modal and the Edit cards so the two forms cannot drift
function RoutingRuleFields({rule, nameDisabled, disabled, width}: RoutingRuleFieldsProps) {
    return (
        <>
            <Form.Input
                    field="name"
                    label="Name"
                    style={{ width }}
                    rules={[required, stringType]}
                    disabled={nameDisabled}
                    initValue={rule?.name}
            />
            <Form.Input
                    field="description"
                    label="Description"
                    style={{ width }}
                    rules={[stringType]}
                    disabled={disabled}
                    initValue={rule?.description}
            />
            <Form.InputNumber
                    field="priority"
                    label="Priority"
                    style={{ width }}
                    rules={[required]}
                    disabled={disabled}
                    initValue={rule?.priority ?? 0}
            />
            <Form.Input
                    field="condition"
                    label="Condition"
                    style={{ width }}
                    rules={[required, stringType]}
                    disabled={disabled}
                    initValue={rule?.condition}
            />
            <Form.TextArea
                    field="actions"
                    label="Actions"
                    style={{ width, overflowY: 'auto', overflowX: 'auto' }}
                    autosize rows={2}
                    placeholder={Locale.RoutingRules.ActionsHint}
                    rules={[required, stringType]}
                    disabled={disabled}
                    initValue={rule?.actions.join("\n")}
            />
        </>
    );
}

export function RoutingRules() {
    const [rules, setRules] = useState<RoutingRuleStatus[]>([]);
    // Bumped on every fetch so each card's form remounts with the stored values and registers a fresh form API
    const [fetchVersion, setFetchVersion] = useState(0);
    const [editingStates, setEditingStates] = useState<boolean[]>([]);
    const [formApis, setFormApis] = useState<(FormApi<any> | null)[]>([]);
    const [isExternalRouting, setIsExternalRouting] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [isAddVisible, setIsAddVisible] = useState(false);
    const [addFormApi, setAddFormApi] = useState<FormApi<any> | null>(null);
    const access = useAccessStore();
    const canModify = access.hasRole(Role.ADMIN) && !isExternalRouting;

    useEffect(() => {
        fetchRoutingRules();
    }, []);

    const fetchRoutingRules = () => {
        setIsLoading(true);
        routingRulesApi()
                .then(data => {
                    if (data.isExternalRouting) {
                        setIsExternalRouting(true);
                    } else {
                        setRules(data);
                        setEditingStates(new Array(data.length).fill(false));
                        setFormApis(new Array(data.length).fill(null));
                        setFetchVersion(version => version + 1);
                        setIsExternalRouting(false);
                    }
                }).catch(() => {
            Toast.error(Locale.RoutingRules.ErrorFetch);
        }).finally(() => {
            setIsLoading(false);
        });
    };

    const handleEdit = (index: number) => {
        setEditingStates(prev => {
            const newStates = [...prev];
            newStates[index] = true;
            return newStates;
        });
    };

    const handleSave = async (index: number) => {
        const formApi = formApis[index];
        if (formApi) {
            let values;
            try {
                values = await formApi.validate();
            } catch {
                return;
            }
            try {
                const updatedRule: RoutingRulesData = {
                    name: rules[index].name,
                    description: values.description ?? "",
                    priority: Number(values.priority),
                    condition: values.condition,
                    actions: parseActions(values.actions),
                };

                await updateRoutingRulesApi(updatedRule);

                Toast.success(Locale.RoutingRules.Update);
                // A priority change moves the rule, so reload to show the stored evaluation order
                fetchRoutingRules();
            } catch {
                // api.post has already shown the error
            }
        }
    };

    const handleCreate = async () => {
        if (!addFormApi) {
            return;
        }
        let values;
        try {
            values = await addFormApi.validate();
        } catch {
            return;
        }
        const newRule: RoutingRulesData = {
            name: values.name.trim(),
            description: values.description ?? "",
            priority: Number(values.priority),
            condition: values.condition.trim(),
            actions: parseActions(values.actions),
        };
        try {
            await createRoutingRuleApi(newRule);
            Toast.success(Locale.RoutingRules.Create);
            setIsAddVisible(false);
            fetchRoutingRules();
        } catch {
            // api.post has already shown the error
        }
    };

    const handleDelete = async (name: string) => {
        try {
            await deleteRoutingRuleApi(name);
            Toast.success(Locale.RoutingRules.Delete);
            fetchRoutingRules();
        } catch {
            // api.post has already shown the error
        }
    };

    const addButton = canModify && (
        <Button theme="solid" onClick={() => setIsAddVisible(true)}>{Locale.RoutingRules.Add}</Button>
    );

    const addRuleModal = (
        <Modal
                title={Locale.RoutingRules.Add}
                visible={isAddVisible}
                onOk={handleCreate}
                onCancel={() => setIsAddVisible(false)}
                width={800}
                destroyOnClose
        >
            <Form
                    labelPosition="left"
                    labelAlign="left"
                    labelWidth={150}
                    getFormApi={setAddFormApi}
            >
                <RoutingRuleFields nameDisabled={false} disabled={false} width={560}/>
            </Form>
        </Modal>
    );

    const setFormApiForIndex = (index: number) => (api: FormApi<any>) => {
        setFormApis(prev => {
            const newApis = [...prev];
            newApis[index] = api;
            return newApis;
        });
    };

    return (
            <>
                {addRuleModal}
                {!isLoading && rules.length > 0 && addButton && (
                    <div style={{marginBottom: '20px'}}>{addButton}</div>
                )}
                {isLoading ? (
                    <div style={{
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        paddingTop: '40px'
                    }}>
                        <Spin size="large" />
                    </div>
                ) : rules.length === 0 ? (
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        gap: '16px',
                        paddingTop: '40px',
                        fontSize: '16px',
                        color: '#666'
                    }}>
                        {isExternalRouting ?
                            "No routing rules available. Routing rules are managed by an external service." :
                            "No routing rules configured. Add rules to manage query routing."
                        }
                        {addButton}
                    </div>
                ) : (
                    rules.map((rule, index) => (
                        <div key={`${rule.name}-${fetchVersion}`} style={{marginBottom: '20px'}}>
                            <Card
                                    shadows='always'
                                    title={
                                        <Space>
                                            {`Routing rule #${index + 1}`}
                                            {!rule.active && <Tag color="red">{Locale.RoutingRules.NotApplied}</Tag>}
                                            {rule.active && rule.error && <Tag color="orange">{Locale.RoutingRules.Errors}</Tag>}
                                        </Space>
                                    }
                                    style={{maxWidth: 800, padding: 20}}
                                    bordered={false}
                                    headerExtraContent={
                                        (access.hasRole(Role.ADMIN) && (
                                            <Space>
                                                <Button onClick={() => handleEdit(index)}>Edit</Button>
                                                {canModify && (
                                                    <Popconfirm
                                                            title={Locale.RoutingRules.DeleteConfirm}
                                                            content={rule.name}
                                                            onConfirm={() => handleDelete(rule.name)}
                                                    >
                                                        <Button type="danger">Delete</Button>
                                                    </Popconfirm>
                                                )}
                                            </Space>
                                        ))
                                    }
                                    footerStyle={{
                                        display: 'flex',
                                        justifyContent: 'flex-end',
                                        ...(editingStates[index] ? {} : { display: 'none' })
                                    }}
                                    footer={
                                        (access.hasRole(Role.ADMIN) && (
                                            <Button onClick={() => handleSave(index)}>Save</Button>
                                        ))
                                    }
                            >
                                {rule.error && (
                                    <Banner type={rule.active ? "warning" : "danger"} closeIcon={null} description={rule.error} style={{marginBottom: '16px'}}/>
                                )}
                                <Form
                                        labelPosition="left"
                                        labelAlign="left"
                                        labelWidth={150}
                                        style={{ paddingRight: '20px' }}
                                        getFormApi={setFormApiForIndex(index)}
                                >
                                    <RoutingRuleFields rule={rule} nameDisabled={true} disabled={!editingStates[index]} width={600}/>
                                </Form>
                            </Card>
                        </div>
                )))}
            </>
    );
}
