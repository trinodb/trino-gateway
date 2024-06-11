import { Form, Button, Toast, Spin } from '@douyinfe/semi-ui';
import styles from './login.module.scss';
import Locale from "../locales";
import { useEffect, useState } from 'react';
import { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { loginFormApi, loginOAuthApi, loginTypeApi } from '../api/webapp/login';
import { useAccessStore } from '../store';

export function Login() {
  const access = useAccessStore();
  const [formApi, setFormApi] = useState<FormApi<any>>();
  const [loginBo, setLoginBo] = useState<Record<string, any>>({});
  const [loginType, setLoginType] = useState<'form' | 'oauth' | 'none'>();

  useEffect(() => {
    loginTypeApi().then(data => {
      setLoginType(data);
    }).catch(() => { });
  }, [])

  const submitForm = () => {
    if (formApi) {
      formApi.validate(['username', 'password'])
        .then(() => {
          loginFormApi(loginBo).then(data => {
            access.updateToken(data.token);
            Toast.success(Locale.Auth.LoginSuccess);
          }).catch(() => { });
        }).catch(() => { });
    }
  }

  const submitOAuth = () => {
    loginOAuthApi({}).then(data => {
      window.location.href = data;
    }).catch(() => { });
  }

  return (
    <div className={styles.main}>
      <div className={styles.login}>
        <div className={styles.component66}>
          <img
            src="/trino-gateway/logo.svg"
            className={styles.logo}
          />
          <div className={styles.header}>
            <p className={styles.text}>
              <span>{Locale.Auth.tips.tip1}</span>
              <span>{Locale.Auth.tips.tip2}</span>
              <span>{Locale.Auth.tips.tip3}</span>
            </p>
          </div>
        </div>
        {loginType == 'form' && (
          <div className={styles.form}>
            <Form className={styles.inputs} getFormApi={setFormApi} onValueChange={values => setLoginBo(values)}>
              <Form.Input
                label={{ text: Locale.Auth.Username }}
                field="username"
                placeholder={Locale.Auth.UsernameTip}
                style={{ width: "100%" }}
                fieldStyle={{ alignSelf: "stretch", padding: 0 }}
                rules={[
                  { required: true, message: 'required error' },
                  { type: 'string', message: 'type error' },
                ]}
              />
              <Form.Input
                label={{ text: Locale.Auth.Password }}
                field="password"
                type="password"
                placeholder={Locale.Auth.PasswordTip}
                style={{ width: "100%" }}
                fieldStyle={{ alignSelf: "stretch", padding: 0 }}
                rules={[
                  { required: true, message: 'required error' },
                  { type: 'string', message: 'type error' },
                ]}
              />
              <Button theme="solid" htmlType='submit' className={styles.button} onClick={submitForm}>
                {Locale.Auth.Login}
              </Button>
            </Form>
          </div>
        )}
        {loginType == 'oauth' && (
          <div className={styles.oauth}>
            <Button theme="solid" className={styles.button} onClick={submitOAuth}>
              {Locale.Auth.OAuth2}
            </Button>
          </div>
        )}
        {loginType == 'none' && (
          <div className={styles.form}>
            <Form className={styles.inputs} getFormApi={setFormApi} onValueChange={values => setLoginBo(values)}>
              <Form.Input
                label={{ text: Locale.Auth.Username }}
                field="username"
                placeholder={Locale.Auth.UsernameTip}
                style={{ width: "100%" }}
                fieldStyle={{ alignSelf: "stretch", padding: 0 }}
                rules={[
                  { required: true, message: 'required error' },
                  { type: 'string', message: 'type error' },
                ]}
              />
              <Form.Input
                label={{ text: Locale.Auth.Password }}
                field="password"
                placeholder={Locale.Auth.NoneAuthTip}
                style={{ width: "100%" }}
                fieldStyle={{ alignSelf: "stretch", padding: 0 }}
                readonly={true}
              />
              <Button theme="solid" htmlType='submit' className={styles.button} onClick={submitForm}>
                {Locale.Auth.Login}
              </Button>
            </Form>
          </div>
        )}
        {loginType == undefined && (
          <div className={styles.undefined}>
            <Spin size="large" />
          </div>
        )}
      </div>
    </div>
  );
}
