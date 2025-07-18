import { useEffect, useState } from "react";
import { routingRulesApi, updateRoutingRulesApi } from "../api/webapp/routing-rules.ts";
import { RoutingRulesData } from "../types/routing-rules";
import { Button, Card, Form, Toast, Spin } from "@douyinfe/semi-ui";
import { FormApi } from "@douyinfe/semi-ui/lib/es/form";
import { Role, useAccessStore } from "../store";
import Locale from "../locales";

export function RoutingRules() {
    const [rules, setRules] = useState<RoutingRulesData[]>([]);
    const [editingStates, setEditingStates] = useState<boolean[]>([]);
    const [formApis, setFormApis] = useState<(FormApi<any> | null)[]>([]);
    const [isExternalRouting, setIsExternalRouting] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const access = useAccessStore();

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
            try {
                const values = formApi.getValues();
                const actionsArray = Array.isArray(values.actions)
                        ? values.actions.map((action: string) => action.trim())
                        : [values.actions.trim()];

                const updatedRule: RoutingRulesData = {
                    ...rules[index],
                    ...values,
                    actions: actionsArray
                };

                await updateRoutingRulesApi(updatedRule);

                setEditingStates(prev => {
                    const newStates = [...prev];
                    newStates[index] = false;
                    return newStates;
                });

                setRules(prev => {
                    const newRules = [...prev];
                    newRules[index] = updatedRule;
                    return newRules;
                });

                Toast.success(Locale.RoutingRules.Update);
            } catch (error) {
                Toast.error(Locale.RoutingRules.ErrorUpdate);
            }
        }
    };

    const setFormApiForIndex = (index: number) => (api: FormApi<any>) => {
        setFormApis(prev => {
            const newApis = [...prev];
            newApis[index] = api;
            return newApis;
        });
    };

    return (
            <>
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
                        justifyContent: 'center',
                        alignItems: 'flex-start',
                        paddingTop: '40px',
                        fontSize: '16px',
                        color: '#666'
                    }}>
                        {isExternalRouting ?
                            "No routing rules available. Routing rules are managed by an external service." :
                            "No routing rules configured. Add rules to manage query routing."
                        }
                    </div>
                ) : (
                    rules.map((rule, index) => (
                        <div key={index} style={{marginBottom: '20px'}}>
                            <Card
                                    shadows='always'
                                    title={`Routing rule #${index + 1}`}
                                    style={{maxWidth: 800, padding: 20}}
                                    bordered={false}
                                    headerExtraContent={
                                        (access.hasRole(Role.ADMIN) && (
                                            <Button onClick={() => handleEdit(index)}>Edit</Button>
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
                                <Form
                                        labelPosition="left"
                                        labelAlign="left"
                                        labelWidth={150}
                                        style={{ paddingRight: '20px' }}
                                        getFormApi={setFormApiForIndex(index)}
                                >
                                    <Form.Input
                                            field="name"
                                            label="Name"
                                            style={{ width: 600 }}
                                            rules={[
                                                {required: true, message: 'required error'},
                                                {type: 'string', message: 'type error'},
                                            ]}
                                            disabled={true}
                                            initValue={rule.name}
                                    />
                                    <Form.Input
                                            field="description"
                                            label="Description"
                                            style={{ width: 600 }}
                                            rules={[
                                                {required: false, message: 'required error'},
                                                {type: 'string', message: 'type error'},
                                            ]}
                                            disabled={!editingStates[index]}
                                            initValue={rule.description}
                                    />
                                    <Form.Input
                                            field="priority"
                                            label="Priority"
                                            style={{ width: 600 }}
                                            rules={[
                                                {required: false, message: 'required error'},
                                                {type: 'string', message: 'type error'},
                                            ]}
                                            disabled={!editingStates[index]}
                                            initValue={rule.priority}
                                    />
                                    <Form.Input
                                            field="condition"
                                            label="Condition"
                                            style={{ width: 600 }}
                                            rules={[
                                                {required: true, message: 'required error'},
                                                {type: 'string', message: 'type error'},
                                            ]}
                                            disabled={!editingStates[index]}
                                            initValue={rule.condition}
                                    />
                                    <Form.TextArea
                                            field="actions"
                                            label="Actions"
                                            style={{ width: 600, overflowY: 'auto', overflowX: 'auto' }}
                                            autosize rows={1}
                                            rules={[
                                                {required: true, message: 'required error'},
                                                {type: 'string', message: 'type error'},
                                            ]}
                                            disabled={!editingStates[index]}
                                            initValue={rule.actions}
                                    />
                                </Form>
                            </Card>
                        </div>
                )))}
            </>
    );
}
